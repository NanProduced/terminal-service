package com.colorlight.terminal.rpc.dto.command;

import java.io.Serializable;

/**
 * 指令封装
 *
 * @author Nan
 */
public class TerminalCommandDTO implements Serializable {

    private static final long serialVersionUID = 6538191347998699017L;

    /**
     * 指令操作类型
     */
    private String authorUrl;

    private Content content;

    /**
     * 屏幕执行方式<p>
     * 0-get, 1-post, 2-put,3-delete
     */
    private Integer karma;

    public TerminalCommandDTO() {

    }

    public TerminalCommandDTO(String authorUrl, Content content, Integer karma) {
        this.authorUrl = authorUrl;
        this.content = content;
        this.karma = karma;
    }

    public String getAuthorUrl() {
        return authorUrl;
    }

    public void setAuthorUrl(String authorUrl) {
        this.authorUrl = authorUrl;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public Integer getKarma() {
        return karma;
    }

    public void setKarma(Integer karma) {
        this.karma = karma;
    }

    public static class Content {

        private String raw;

        public Content() {}

        public Content(String raw) {
            this.raw = raw;
        }

        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }
    }
}
