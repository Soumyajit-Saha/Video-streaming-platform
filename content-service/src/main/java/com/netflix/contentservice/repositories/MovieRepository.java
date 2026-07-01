package com.netflix.contentservice.repositories;

import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository  extends JpaRepository<Movie, String> {
    List<Movie> findByGenre(Genre genre);
    List<Movie> findByTitleContainingIgnoreCase(String title);
}
