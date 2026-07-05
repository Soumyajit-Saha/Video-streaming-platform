package com.netflix.streamingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamingResponse {
    private String movieId;
    private String streamingURL; //Presigned HLS master playlist URL
    private String quality; // available qualities
    private long expiresInMinutes; // URL expiry time
}
