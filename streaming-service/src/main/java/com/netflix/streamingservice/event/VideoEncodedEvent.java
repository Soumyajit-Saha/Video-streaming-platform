package com.netflix.streamingservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed from Kafka topic: video.encoded
 * Published by Encoding Service after FFmpeg processing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoEncodedEvent {
    private String movieId;
    private String hlsUrl; // Master playlist usage for streaming
    private String masterPlaylistKey; // S3 Key of master.m3u8
    private boolean success;
    private String errorMessage; // If encoding failed
}
