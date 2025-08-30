package com.colorlight.terminal.application.domain.sensor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * GpsReport类继承自SensorReport，用于封装GPS相关的传感器数据。
 * 该上报类型在JSON序列化时使用`gps`作为标识符。
 *
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("gps")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GpsReport extends SensorReport{

    @Serial
    private static final long serialVersionUID = 9090415141890328473L;

    /**
     * 表示地理位置的经度，用于GPS报告中定位设备的具体位置。
     * 值为正表示东经，值为负表示西经。
     */
    private Double longitude;

    /**
     * 表示地理位置的纬度，用于GPS报告中定位设备的具体位置。
     * 值为正表示北纬，值为负表示南纬。
     */
    private Double latitude;

    /**
     * 精度
     */
    private Double accuracy;
    /**
     * 海拔
     */
    private Double altitude;
    /**
     * 速度（一般为0）
     */
    private Double speed;

    /**
     * 方向
     */
    private Double direct;

    /**
     * 搜索到的卫星数
     */
    private Integer satellites;

    /**
     * 封装了移动通信网络中的基站信息，这些信息对于辅助GPS定位特别有用。
     * 包含基站编号、移动网络国家代码、移动网络号码和位置区域码等字段，
     * 用于在GPS信号较弱或不可用时提供辅助定位信息。
     * <p>一般没这个</p>
     */
    private CellInfo cellInfo;

    /**
     * 该列表封装了GPS报告中所有可见卫星的详细信息。每个元素都是一个GsvDTO对象，代表了一颗特定卫星的数据，
     * 包括方位角、仰角、伪随机噪声码编号以及信号噪声比等关键参数。
     * 通过这些数据，可以全面了解设备接收到的卫星信号状态及其对定位精度的影响。
     * <p>一般没这个</p>
     */
    private List<GsvDTO> gsv;

    /**
     * 无效标识值（播放盒定的）
     */
    private static final Double INVALID_GPS_DOUBLE_VALUE = -1.0;

    @Override
    public boolean validate() {
        // 检查null值和无效标识值
        if (latitude == null || longitude == null) return false;
        if (Objects.equals(INVALID_GPS_DOUBLE_VALUE, latitude) || Objects.equals(INVALID_GPS_DOUBLE_VALUE, longitude)) return false;
        
        // 检查GPS坐标范围
        return latitude >= -90.0 && latitude <= 90.0 && longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * GsvDTO类用于封装GPS报告中关于卫星的具体信息。此类实现了Serializable接口，允许其实例被序列化以便存储或在网络中传输。
     * 该类包含四个主要属性：方位角(azi)、仰角(ele)、伪随机噪声码编号(prn)以及信号噪声比(snr)，这些属性共同描述了设备接收到的每颗卫星的关键参数。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GsvDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = -1357956376258865664L;

        /**
         * 表示卫星的方位角，用于GPS报告中确定设备相对于卫星的位置。
         * 方位角是从正北方向开始，顺时针测量到目标方向的角度。
         * 该值通常以度为单位，并且范围是0到360度。
         */
        private Integer azi;

        /**
         * 表示卫星的仰角，用于GPS报告中确定设备相对于卫星的位置。
         * 仰角是从水平面开始向上测量到目标方向的角度。
         * 该值通常以度为单位，并且范围是0到90度。
         */
        private Integer ele;

        /**
         * 表示卫星的伪随机噪声码编号（PRN），用于GPS报告中唯一标识每颗卫星。
         * PRN编号是GPS系统中用来区分不同卫星的唯一标识符，范围从1到32。
         */
        private Integer prn;

        /**
         * 表示卫星的信号噪声比（Signal-to-Noise Ratio, SNR），用于GPS报告中评估接收到的卫星信号质量。
         * SNR值越高，表示信号质量越好，通常以分贝（dB）为单位。
         */
        private Integer snr;
    }

    /**
     * CellInfo类封装了移动通信网络中的基站信息，这些信息对于辅助GPS定位特别有用。
     * 该类实现了Serializable接口，允许其实例被序列化以便存储或在网络中传输。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CellInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = -4343457916058579195L;

        /**
         * 基站编号，用于标识移动通信网络中的特定基站。
         * 该字段在与位置相关的报告中尤为重要，特别是在GPS信号较弱或不可用时，通过CID可以辅助定位设备的大致位置。
         */
        private Integer cid;

        /**
         * 移动网络国家代码。该字段用于标识移动通信网络所在的国家或地区。
         * 例如，460代表中国。
         */
        private Integer mnc;

        /**
         * 表示移动网络号码，用于区分不同的运营商。
         * 该字段的具体值代表了不同的中国主要移动通信运营商：
         * - 0 代表中国移动
         * - 1 代表中国联通
         * - 2 代表中国电信
         */
        private Integer mcc;

        /**
         * 位置区域码。该字段用于标识移动通信网络中的特定位置区域，通常与CID（基站编号）一起使用来确定设备的位置。
         * 在GPS信号弱或不可用的情况下，LAC可以辅助提供更精确的位置信息。
         */
        private Integer lac;
    }

}
