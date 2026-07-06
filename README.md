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

> The streaming endpoints depend on Redis and the underlying playlist data being present, so they may return 404 unless the expected streaming metadata has been generated first.
