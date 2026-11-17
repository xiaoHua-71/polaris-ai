package com.xiaohua.polaris.tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

@ToolGroup(group = "time")
public class TimeTools {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    @Tool(name = "getCurrentTime", description = "获取指定时区的当前日期和时间。需要回答“现在几点”“今天几号”等实时问题时使用。")
    public String getCurrentTime(
            @ToolParam(description = "IANA 时区名称，例如 Asia/Shanghai、America/New_York；不传默认 Asia/Shanghai", required = false)
            String zoneId) {
        ZoneId zone = StringUtils.hasText(zoneId) ? ZoneId.of(zoneId.trim()) : DEFAULT_ZONE;
        return ZonedDateTime.now(zone).format(FORMATTER);
    }
}
