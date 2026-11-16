package com.xiaohua.polaris.chat;

import org.springframework.util.StringUtils;

public record ExtractedPerson(String name, Integer age, String city, String occupation) {

    public boolean hasNoContent() {
        return !StringUtils.hasText(name) && age == null
                && !StringUtils.hasText(city) && !StringUtils.hasText(occupation);
    }
}
