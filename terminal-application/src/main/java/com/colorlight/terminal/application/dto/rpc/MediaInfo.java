package com.colorlight.terminal.application.dto.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 素材信息VO
 * <p>用于承载RPC接口返回的素材ID和素材名称</p>
 *
 * @author Nan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = -6047735882185819707L;
    /**
     * 素材ID
     */
    private Integer mediaId;

    /**
     * 素材名称
     */
    private String mediaName;
}
