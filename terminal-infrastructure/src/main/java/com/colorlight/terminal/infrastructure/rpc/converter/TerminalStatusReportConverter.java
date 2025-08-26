package com.colorlight.terminal.infrastructure.rpc.converter;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.rpc.dto.report.TerminalStatusReportDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalListItemDTO;
import org.springframework.stereotype.Component;

/**
 * 终端状态报告转换器
 * 用于在Domain对象和DTO之间进行转换
 * 
 * @author Demon
 */
@Component
public class TerminalStatusReportConverter {

    /**
     * 转换为完整的DTO
     */
    public TerminalStatusReportDTO convertToDTO(Long deviceId, TerminalStatusReport report) {
        if (report == null) {
            return null;
        }
        
        TerminalStatusReportDTO dto = new TerminalStatusReportDTO();
        dto.setDeviceId(deviceId);
        
        // 转换终端基本信息
        if (report.getTerminal() != null) {
            TerminalStatusReportDTO.TerminalDTO terminalDTO = new TerminalStatusReportDTO.TerminalDTO();
            terminalDTO.setName(report.getTerminal().getName());
            terminalDTO.setLeddescription(report.getTerminal().getLeddescription());
            terminalDTO.setReportTime(report.getTerminal().getReportTime());
            dto.setTerminal(terminalDTO);
        }
        
        // 转换电源状态
        if (report.getPowerstatus() != null) {
            TerminalStatusReportDTO.PowerStatusDTO powerStatusDTO = new TerminalStatusReportDTO.PowerStatusDTO();
            powerStatusDTO.setPowerstatus(report.getPowerstatus().getPowerstatus());
            powerStatusDTO.setReportTime(report.getPowerstatus().getReportTime());
            dto.setPowerstatus(powerStatusDTO);
        }
        
        // 转换详细信息
        convertInfoWrapper(report, dto);
        
        // 转换版本和内容信息
        convertVsns(report, dto);
        
        // 转换屏幕尺寸
        convertDimension(report, dto);
        
        // 转换音量
        convertVolume(report, dto);

        // 转换输入模式
        convertInputMode(report, dto);

        // 转换网络接口状态
        convertIfStatus(report, dto);
        
        // 转换亮度和色温
        convertBrightnessAndColorTemp(report, dto);
        
        // 转换新版RTC
        convertNewRtc(report, dto);
        
        // 转换本地化信息
        convertLocale(report, dto);
        
        // 转换所有亮度信息
        convertAllBrightnessInfo(report, dto);
        
        // 转换4G信息
        dto.set_4ginfo(report.get_4ginfo());
        
        return dto;
    }

    /**
     * 转换为终端列表项（提取关键信息）
     */
    public TerminalListItemDTO convertToTerminalListItem(Long deviceId, TerminalStatusReport report) {
        if (report == null) {
            return null;
        }
        
        TerminalListItemDTO item = new TerminalListItemDTO();
        item.setDeviceId(deviceId);
        
        // 提取终端名称
        if (report.getTerminal() != null) {
            item.setTerminalName(report.getTerminal().getName());
        }
        
        // 提取设备信息
        if (report.getInfo() != null && report.getInfo().getInfo() != null) {
            var info = report.getInfo().getInfo();
            item.setDeviceVersion(info.getVername());
            item.setDeviceModel(info.getModel());
            item.setSerialNumber(info.getSerialno());
            item.setUptime(info.getUp());
            
            // 计算存储使用百分比
            if (info.getStorage() != null) {
                long total = info.getStorage().getTotal();
                long free = info.getStorage().getFree();
                if (total > 0) {
                    double usagePercent = ((double) (total - free) / total) * 100;
                    item.setStorageUsagePercent(Math.round(usagePercent * 100.0) / 100.0); // 保留两位小数
                }
            }
            
            // 提取当前节目名称
            if (info.getPlaying() != null) {
                item.setCurrentProgramName(info.getPlaying().getName());
            }
        }
        
        // 提取亮度
        if (report.getBrightnessandcolortemp() != null) {
            item.setBrightness(report.getBrightnessandcolortemp().getBrightness());
        }
        
        // 提取音量
        if (report.getVolume() != null) {
            item.setVolume(report.getVolume().getMusicvolume());
        }
        
        // 提取分辨率
        if (report.getDimension() != null) {
            String resolution = report.getDimension().getWidth() + "x" + report.getDimension().getHeight();
            item.setResolution(resolution);
        }
        
        // 提取网络类型
        extractNetworkType(report, item);

        return item;
    }

    private void convertInfoWrapper(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getInfo() != null) {
            TerminalStatusReportDTO.InfoWrapperDTO infoWrapperDTO = new TerminalStatusReportDTO.InfoWrapperDTO();
            infoWrapperDTO.setReportTime(report.getInfo().getReportTime());
            
            if (report.getInfo().getInfo() != null) {
                TerminalStatusReportDTO.InfoDTO infoDTO = new TerminalStatusReportDTO.InfoDTO();
                var info = report.getInfo().getInfo();
                
                infoDTO.setVername(info.getVername());
                infoDTO.setSerialno(info.getSerialno());
                infoDTO.setModel(info.getModel());
                infoDTO.setUp(info.getUp());
                
                // 转换内存信息
                if (info.getMem() != null) {
                    TerminalStatusReportDTO.MemDTO memDTO = new TerminalStatusReportDTO.MemDTO();
                    memDTO.setTotal(info.getMem().getTotal());
                    memDTO.setFree(info.getMem().getFree());
                    infoDTO.setMem(memDTO);
                }
                
                // 转换存储信息
                if (info.getStorage() != null) {
                    TerminalStatusReportDTO.StorageDTO storageDTO = new TerminalStatusReportDTO.StorageDTO();
                    storageDTO.setTotal(info.getStorage().getTotal());
                    storageDTO.setFree(info.getStorage().getFree());
                    infoDTO.setStorage(storageDTO);
                }
                
                // 转换播放状态
                if (info.getPlaying() != null) {
                    TerminalStatusReportDTO.PlayingDTO playingDTO = new TerminalStatusReportDTO.PlayingDTO();
                    playingDTO.setName(info.getPlaying().getName());
                    playingDTO.setPath(info.getPlaying().getPath());
                    playingDTO.setSource(info.getPlaying().getSource());
                    infoDTO.setPlaying(playingDTO);
                }
                
                infoWrapperDTO.setInfo(infoDTO);
            }
            
            dto.setInfo(infoWrapperDTO);
        }
    }

    private void convertVsns(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getVsns() != null) {
            TerminalStatusReportDTO.VsnsDTO vsnsDTO = new TerminalStatusReportDTO.VsnsDTO();
            vsnsDTO.setReportTime(report.getVsns().getReportTime());
            
            // 简化处理内容列表
            if (report.getVsns().getContents() != null) {
                // 这里可以根据需要进行更详细的转换
                vsnsDTO.setContents(null); // 暂时设为null，避免复杂转换
            }
            
            // 转换播放状态
            if (report.getVsns().getPlaying() != null) {
                TerminalStatusReportDTO.PlayingDTO playingDTO = new TerminalStatusReportDTO.PlayingDTO();
                playingDTO.setName(report.getVsns().getPlaying().getName());
                playingDTO.setPath(report.getVsns().getPlaying().getPath());
                playingDTO.setSource(report.getVsns().getPlaying().getSource());
                vsnsDTO.setPlaying(playingDTO);
            }
            
            dto.setVsns(vsnsDTO);
        }
    }

    private void convertDimension(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getDimension() != null) {
            TerminalStatusReportDTO.DimensionDTO dimensionDTO = new TerminalStatusReportDTO.DimensionDTO();
            dimensionDTO.setDclk(report.getDimension().getDclk());
            dimensionDTO.setFps(report.getDimension().getFps());
            dimensionDTO.setHeight(report.getDimension().getHeight());
            dimensionDTO.setWidth(report.getDimension().getWidth());
            dimensionDTO.setReal_height(report.getDimension().getReal_height());
            dimensionDTO.setReal_width(report.getDimension().getReal_width());
            dimensionDTO.setReportTime(report.getDimension().getReportTime());
            dto.setDimension(dimensionDTO);
        }
    }

    private void convertVolume(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getVolume() != null) {
            TerminalStatusReportDTO.VolumeDTO volumeDTO = new TerminalStatusReportDTO.VolumeDTO();
            volumeDTO.setMusicvolume(report.getVolume().getMusicvolume());
            volumeDTO.setReportTime(report.getVolume().getReportTime());
            dto.setVolume(volumeDTO);
        }
    }

    private void convertInputMode(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getInputmode() != null) {
            TerminalStatusReportDTO.InputModeDTO inputModeDTO = new TerminalStatusReportDTO.InputModeDTO();
            inputModeDTO.setReportTime(report.getInputmode().getReportTime());
            dto.setInputmode(inputModeDTO);
        }
    }

    private void convertIfStatus(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getIfStatus() != null && report.getIfStatus().getTypes() != null) {
            TerminalStatusReportDTO.IfStatusDTO ifStatusDTO = new TerminalStatusReportDTO.IfStatusDTO();
            
            // 简化网络接口转换
            var netInterfaceDTOs = report.getIfStatus().getTypes().stream()
                    .map(netInterface -> {
                        TerminalStatusReportDTO.IfStatusDTO.NetInterfaceDTO netInterfaceDTO = 
                                new TerminalStatusReportDTO.IfStatusDTO.NetInterfaceDTO();
                        netInterfaceDTO.setType(netInterface.getType());
                        netInterfaceDTO.setEnabled(netInterface.getEnabled());
                        netInterfaceDTO.setConnected(netInterface.getConnected());
                        netInterfaceDTO.setOperstate(netInterface.getOperstate());
                        netInterfaceDTO.setMac(netInterface.getMac());
                        netInterfaceDTO.setSSID(netInterface.getSSID());
                        netInterfaceDTO.setSpeed(netInterface.getSpeed());
                        netInterfaceDTO.setState(netInterface.getState());
                        return netInterfaceDTO;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            ifStatusDTO.setTypes(netInterfaceDTOs);
            dto.setIfStatus(ifStatusDTO);
        }
    }

    private void convertBrightnessAndColorTemp(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getBrightnessandcolortemp() != null) {
            TerminalStatusReportDTO.BrightnessAndColorTempDTO brightnessDTO = 
                    new TerminalStatusReportDTO.BrightnessAndColorTempDTO();
            brightnessDTO.setBrightness(report.getBrightnessandcolortemp().getBrightness());
            brightnessDTO.setColortemperature(report.getBrightnessandcolortemp().getColortemperature());
            brightnessDTO.setReportTime(report.getBrightnessandcolortemp().getReportTime());
            dto.setBrightnessandcolortemp(brightnessDTO);
        }
    }

    private void convertNewRtc(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getNewrtc() != null) {
            TerminalStatusReportDTO.NewRtcDTO newRtcDTO = new TerminalStatusReportDTO.NewRtcDTO();
            newRtcDTO.setTime(report.getNewrtc().getTime());
            newRtcDTO.setTimezoneId(report.getNewrtc().getTimezoneId());
            newRtcDTO.setTimezone(report.getNewrtc().getTimezone());
            newRtcDTO.setIsautotime(report.getNewrtc().getIsautotime());
            newRtcDTO.setReportTime(report.getNewrtc().getReportTime());
            dto.setNewrtc(newRtcDTO);
        }
    }

    private void convertLocale(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getLocale() != null) {
            TerminalStatusReportDTO.LocaleInfoDTO localeDTO = new TerminalStatusReportDTO.LocaleInfoDTO();
            localeDTO.setLanguage(report.getLocale().getLanguage());
            localeDTO.setCountry(report.getLocale().getCountry());
            dto.setLocale(localeDTO);
        }
    }

    private void convertAllBrightnessInfo(TerminalStatusReport report, TerminalStatusReportDTO dto) {
        if (report.getAllbrightnessinfo() != null) {
            TerminalStatusReportDTO.AllBrightnessInfoDTO allBrightnessDTO = 
                    new TerminalStatusReportDTO.AllBrightnessInfoDTO();
            allBrightnessDTO.setRealTimeBrightValue(report.getAllbrightnessinfo().getRealTimeBrightValue());
            allBrightnessDTO.setSavedBrightValue(report.getAllbrightnessinfo().getSavedBrightValue());
            allBrightnessDTO.setIsbShowOn(report.getAllbrightnessinfo().isIsbShowOn());
            allBrightnessDTO.setHasSensor(report.getAllbrightnessinfo().isHasSensor());
            allBrightnessDTO.setSensorBright(report.getAllbrightnessinfo().getSensorBright());
            dto.setAllbrightnessinfo(allBrightnessDTO);
        }
    }

    private void extractNetworkType(TerminalStatusReport report, TerminalListItemDTO item) {
        if (report.getIfStatus() != null && report.getIfStatus().getTypes() != null) {
            String networkType = report.getIfStatus().getTypes().stream()
                    .filter(netInterface -> netInterface.getConnected() == 1)
                    .map(netInterface -> netInterface.getType())
                    .findFirst()
                    .orElse("--");
            item.setNetworkType(networkType);
        }
    }


}
