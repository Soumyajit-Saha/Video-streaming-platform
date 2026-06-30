package com.netflix.contentservice.model;

/**
 * Tracks the video processing lifecycle
 *
 * FLOW:
 * PENDING -> UPLOADING -> ENCODING -> ENCODED -> READY
 *                                  -> FAILED
 *
 */
public enum VideoStatus {
    PENDING, // movie added but not uploaded
    UPLOADING, // raw video uploaded to S3
    ENCODING, // FFmpeg is encoding the video
    ENCODED, // Encoding complete
    READY, // HLS playlist ready - cane be streamed
    FAILED // Encoding Failed
}
