package com.gas.forecast.business.skill;

import com.gas.forecast.common.core.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 从 classpath 加载 Skill 指令和指定参考协议。 */
@Component
public class SkillPromptLoader {

    public String load(String skillName, String reference) {
        StringBuilder prompt = new StringBuilder(read("skills/" + skillName + "/SKILL.md"));
        if (reference != null && !reference.isBlank()) {
            prompt.append("\n\n# 输出协议\n").append(read("skills/" + skillName + "/references/" + reference));
        }
        return prompt.toString();
    }

    private String read(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException("无法加载Skill资源：" + path);
        }
    }
}
