package com.colorlight.terminal.infrastructure.websocket.processor.v11.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommandResponse {

    private int id;

    private int post;

    @JsonProperty("author_url")
    private String authorUrl;

    private Content content;

    private int karma;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Content {
        private String raw;
    }
}
