package com.netflix.contentservice.controller;

import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.services.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/movies")
@Slf4j
@RequiredArgsConstructor
public class ContentController {
    private final ContentService contentService;
    // Add new movie to catalog

    @PostMapping
    public ResponseEntity<MovieResponse> addMovie(
            @Valid @RequestBody MovieRequest movieRequest){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contentService.addMovie(movieRequest));
    }

    @GetMapping
    public ResponseEntity<MovieResponse> getAllMovies(){
        return ResponseEntity.ok(contentService.getAllMovies());
    }
}
