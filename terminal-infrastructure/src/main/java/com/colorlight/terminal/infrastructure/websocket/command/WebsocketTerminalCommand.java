package com.colorlight.terminal.infrastructure.websocket.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebsocketTerminalCommand {

    private List<WebsocketCommand> data;

    @JsonProperty("led_id")
    private Integer ledId;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WebsocketCommand {
        private Integer id;
        private Integer post;
        @JsonProperty("author_url")
        private String authorUrl;
        private Integer karma;
        private WebsocketContent content;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WebsocketContent {
        private String raw;
    }
}
