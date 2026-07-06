# Video Streaming Platform

This project contains three services:

- Content service: http://localhost:8081
- Video service: http://localhost:8082
- Streaming service: http://localhost:8084

## Prerequisites

### Download FFmpeg

Download the latest FFmpeg release for your operating system:

- **Windows**: Download from [ffmpeg.org/download.html](https://ffmpeg.org/download.html) or use:
  ```bash
  # Using Chocolatey (if installed)
  choco install ffmpeg
  ```
- **macOS**: 
  ```bash
  brew install ffmpeg
  ```
- **Linux (Ubuntu/Debian)**:
  ```bash
  sudo apt-get update
  sudo apt-get install ffmpeg
  ```

Verify the installation:
```bash
ffmpeg -version
```

## Start dependencies

```bash
docker compose up -d redis mysql zookeeper kafka
```

## Test the endpoints

### 1) Content service

Create a movie:

```bash
curl -X POST http://localhost:8081/api/v1/movies \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Inception",
    "description": "A mind-bending sci-fi thriller",
    "genre": "SCI_FI",
    "director": "Christopher Nolan",
    "cast": "Leonardo DiCaprio, Joseph Gordon-Levitt",
    "releaseYear": 2010,
    "rating": 8.8,
    "thumbnailUrl": "https://example.com/inception.jpg",
    "durationMinutes": 148
  }'
```

Get all movies:

```bash
curl http://localhost:8081/api/v1/movies
```

Get movies by genre:

```bash
curl http://localhost:8081/api/v1/movies/genre/SCI_FI
```

Get a single movie:

```bash
curl http://localhost:8081/api/v1/movies/1
```

Search movies by title:

```bash
curl "http://localhost:8081/api/v1/movies/search?title=Inception"
```

### 2) Video service

Upload a video for a movie:

```bash
curl -X POST "http://localhost:8082/api/v1/videos/upload/1" \
  -F "file=@/path/to/video.mp4"
```

### 3) Streaming service

Get a streaming URL for a movie:

```bash
curl http://localhost:8084/api/v1/stream/1
```

Get a signed playlist:

```bash
curl "http://localhost:8084/api/v1/stream/1/playlist?path=some/path/master.m3u8"
```

Here is a full end-to-end example of the flow in the service.

1. The user requests the stream entry point
- The player calls:
  - GET /api/v1/stream/123

2. The controller looks up the Redis playlist key
- In the controller, it reads Redis using the key:
  - streaming:playlist123

3. The service checks Redis cache for the presigned URL
- In StreamingService, it builds a cache key:
  - streaming:url:123
- If the URL is already cached, it returns it directly.
- Otherwise, it generates a new one.

4. The presigned URL is generated from S3
- Suppose the S3 object is:
  - Bucket: my-video-bucket
  - Key: videos/123/master.m3u8
- The service calls AWS S3 presigner and gets a temporary URL like:
  - https://my-video-bucket.s3.amazonaws.com/videos/123/master.m3u8?X-Amz-Algorithm=...
- That URL is returned in the first endpoint response.

5. The player loads the HLS master playlist
- The player uses the returned presigned URL to fetch the master playlist from S3.

6. The player requests the quality-specific playlist
- The player then calls the second endpoint:
  - GET /api/v1/stream/123/playlist?path=videos/123/720p.m3u8

7. The service reads the playlist file from S3
- The server reads the .m3u8 file stored in S3.

8. The service rewrites playlist references into signed URLs
- If the playlist contains lines like:
  - segment1.ts
  - segment2.ts
- the service turns them into presigned S3 URLs.

9. The player downloads the video segments
- The player uses those signed URLs to fetch the .ts segment files one by one.

10. Playback begins
- Once the segments are loaded, the video starts playing.

In short:
- First endpoint: gives the player a signed entry URL for the master playlist.
- Second endpoint: gives the player a signed playlist for a specific quality.
- The playlist then points to signed segment URLs, so playback can continue securely.


> The streaming endpoints depend on Redis and the underlying playlist data being present, so they may return 404 unless the expected streaming metadata has been generated first.
