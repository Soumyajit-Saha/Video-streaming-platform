package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoEncodedEvent;
import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingService {
    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg,path}")
    private String ffmpegPath;

    @Value("${encoding.base-path}")
    private String basePath;

    private static final String VIDEO_ENCODED_TOPIC = "video.encoded";

    // Video qualities to encode
    // Format: resolution, bitrate, height

    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
            new int[]{1920, 5000, 1080}, // 1080p - 5000k bitrate
            new int[]{1280, 2800, 720}, // 720p - 2800k bitrate
            new int[]{854, 1200, 480}, // 480 - 1200k bitrate
            new int[]{640, 800, 360} // 360, 800k bitrate
    );

    /**
     * Main encoding pipeline
     *
     * Steps:
     * 1. Download raw video from s3.
     * 2. Encode to multiple qualities using FFmpeg.
     * 3. Generate HLS playlist (.m3u8) for each quality.
     * 4. Create master playlist.
     * 5. Upload all encoded files back to S3.
     * 6. Publish video encoded event to kafka
     * @param event: Event for video uploaded for encoding
     */

    public void encodeVideo(VideoUploadedEvent event) {
        log.info("Starting encoding platform for movie: {}", event.getMovieId());

        // Create unique path for the movie in the local file system
        String jobPath = basePath + "/" + event.getMovieId();

        try{
            // Create temp directories
            Files.createDirectories(Paths.get(jobPath));
            Files.createDirectories(Paths.get(jobPath + "/encoded"));

            // Step 1: Download raw video from s3
            String localVideoPath = jobPath + "/raw_vodeo.mp4";
            downloadFromS3(event.getVideoKey(), localVideoPath);
            log.info("Raw video downloaded to: {}",  localVideoPath);

            // Step 2 & 3: Encode to multiple qualities and generate multiple HLS
            for (int[] quality : VIDEO_QUALITIES) {
                int width = quality[0];
                int bitrate = quality[1];
                int height = quality[2];

                String qualityDIr = jobPath + "/encoded/" + height + "p";
                Files.createDirectories(Paths.get(qualityDIr));

                encodeToHLS(localVideoPath, qualityDIr, width, height, bitrate);
                log.info("Encoded {}p successfully", height);
            }

            // Step 4: Generate Master playlist
            String masterPlayListPath = jobPath + "/encoded/master.m3u8";
            generateMasterPlaylist(masterPlayListPath);
            log.info("Master Playlist generated");

            // Step 5: Upload all resources file to S3
            String encodingPrefix = "encoded/" + event.getMovieId() + "/";
            uploadEncodedFileToS3(jobPath + "/encoded", encodingPrefix);
            log.info("All encoded files uploaded to S3");

            // Step 6: Publish VideoEncodedEvent to Kafka
            String masterPlaylistKey = encodingPrefix + "master.m3u8";
            String hlsUrl = "https://" + bucketName + ".s3.amazonaws.com/" + masterPlaylistKey;

            VideoEncodedEvent encodedEvent = new VideoEncodedEvent(
                    event.getMovieId(),
                    hlsUrl,
                    masterPlaylistKey,
                    true,
                    null
            );
            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), encodedEvent);
            log.info("VideoEncodedEvent published for movie: {}", event.getMovieId());
        } catch (Exception e){
            log.error("Encoding failed for movie: {} - {}", event.getMovieId(), e.getMessage());

            // publish failure event to Kafka
            VideoEncodedEvent videoEncodedFailureEvent = new VideoEncodedEvent(
                    event.getMovieId(),
                    null,
                    null,
                    false,
                    e.getMessage()
            );
            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), videoEncodedFailureEvent);
        }
        finally {
            // clean temp files
            cleanupTempFiles(jobPath);
        }
    }

    /**
     * Download file from S3 to local path
     */
    private void downloadFromS3(String s3Key, String localPath) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        s3Client.getObject(getObjectRequest, Paths.get(localPath));
    }

    /**
     * Encode video to HLS using FFmpeg
     *
     * FFmpeg command created:
     *  - Multiple .ts segmented files(10 seconds each)
     *  - A .m3u8 playlist file for this quality
     * @param inputPath
     * @param outputDir
     * @param width
     * @param height
     * @param bitrate
     * @throws IOException
     * @throws InterruptedException
     */
    private void encodeToHLS(String inputPath, String outputDir, int width, int height, int bitrate) throws IOException, InterruptedException {
        String playlistPath = outputDir + "/playlist.m3u8";
        String segmentPattern = outputDir + "/segment_%03d.ts";

        // FFmpeg Command for HLS encoding

        List<String> command = Arrays.asList(
                ffmpegPath,
                "-i", inputPath, // Input file
                "-vf", "scale=" + width + ":" + height, // Scale to resolution
                "-c:v", "libx264", // video codec
                "-b:v", bitrate + "k", // video bitrate
                "-c:a", "aac", // audio codec
                "-b:a", "128k", // audio bitrate
                "-hls_time", "10", // 10 second segments
                "-hls_list_size", "0", // keep all segments
                "-hls_segment_filename", segmentPattern, // segment naming
                "-f", "hls", // output format
                playlistPath // output playlist
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if(exitCode != 0){
            throw new RuntimeException(("FFmpeg encoding failed with exit code" + exitCode));
        }
    }

    /**
     * Generates master HLS playlist that references all quality playlists.
     * This is the file the video player downloads first
     * @param masterPlayListPath
     * @throws IOException
     */
    private void generateMasterPlaylist(String masterPlayListPath) throws IOException{
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("EXT-X-VERSION:3\n\n");

        // Add each quality to master playlist

        int[][] qualities = {{1920, 5000, 1080}, {1280, 2800, 720},
                            {854, 1200, 480}, {640, 800, 360}};

        for(int [] q : qualities){
            int width = q[0];
            int bitrate = q[1];
            int height = q[2];

            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(bitrate*1000)
                    .append(", RESOLUTION=").append(width).append("x").append(height)
                    .append(",CODECS=\"avc1.42e01e,mp4a.40.2\"\n");
            master.append(height).append("p/playlist.m3u8\n\n");

            Files.writeString(Paths.get(masterPlayListPath), master.toString());
        }
    }

    /**
     * Upload all encoded files from local directory back to S3
     * @param localDir
     * @param s3Prefix
     */
    private void uploadEncodedFileToS3(String localDir, String s3Prefix) throws IOException{
        File directory = new File(localDir);
        uploadDirectoryToS3(directory, localDir, s3Prefix);
    }

    private void uploadDirectoryToS3(File dir, String baseDir, String s3Prefix) throws IOException {
        for (File file: dir.listFiles()){
            if (file.isDirectory()) {
                uploadDirectoryToS3(file, baseDir, s3Prefix);
            } else {
                String relativePath = file.getAbsolutePath()
                        .substring(baseDir.length() + 1)
                        .replace("\\", "/");
                String s3Key = s3Prefix + relativePath;
                String contentType = file.getName().endsWith(".m3u8")
                        ? "application/x-mpegURL"
                        : "video.MP2T";

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
                log.debug("Uploaded: {}", s3Key);
            }
        }
    }

    /**
     * Clean up temp files after encoding
     */
    private void cleanupTempFiles(String jobPath) {
        try {
            Path dirPath = Paths.get(jobPath);
            if(Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.info("Temp files cleaned up for job: {}", jobPath);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp files: {}", e.getMessage());
        }
    }
}
