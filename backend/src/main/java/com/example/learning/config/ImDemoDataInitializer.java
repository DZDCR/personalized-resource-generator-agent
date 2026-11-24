package com.example.learning.config;

import com.example.learning.dto.im.ConversationSummary;
import com.example.learning.entity.ChatConversationEntity;
import com.example.learning.entity.ChatMemberEntity;
import com.example.learning.entity.StudentEntity;
import com.example.learning.repository.ChatConversationRepository;
import com.example.learning.repository.ChatMemberRepository;
import com.example.learning.repository.StudentRepository;
import com.example.learning.service.ImService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 课程项目演示数据：用户A / 用户B / 用户C + 示例群聊。
 * <p>没有登录体系时，前端通过 /api/im/demo/users 切换身份联调。
 * 生产环境可设置 app.im.seed-demo=false 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.im", name = "seed-demo", havingValue = "true", matchIfMissing = true)
public class ImDemoDataInitializer implements ApplicationRunner {

    private static final String GROUP_TITLE = "示例群聊";
    private static final String LEGACY_GROUP_TITLE = "软件工程课程群";
    private static final String[] DEMO_NAMES = {"用户A", "用户B", "用户C"};
    private static final String[] DEMO_NUMBERS = {"demo-001", "demo-002", "demo-003"};

    private final StudentRepository studentRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMemberRepository memberRepository;
    private final ImService imService;

    @Override
    public void run(ApplicationArguments args) {
        List<StudentEntity> students = new ArrayList<>(studentRepository.findAll(Sort.by("id")));
        while (students.size() < 3) {
            int index = students.size();
            StudentEntity student = new StudentEntity();
            student.setStudentNo(uniqueStudentNo(DEMO_NUMBERS[index]));
            student.setName(DEMO_NAMES[index]);
            student.setMajor("软件工程");
            student.setGrade("2026");
            students.add(studentRepository.save(student));
        }

        // 同步演示身份名称；同时把历史成员快照和消息发送者快照改名。
        for (int i = 0; i < 3; i++) {
            StudentEntity student = students.get(i);
            String name = DEMO_NAMES[i];
            student.setName(name);
            if (student.getStudentNo() == null || student.getStudentNo().isBlank()) {
                student.setStudentNo(uniqueStudentNo(DEMO_NUMBERS[i]));
            }
            student = studentRepository.save(student);
            students.set(i, student);

            for (ChatMemberEntity member : memberRepository.findByUserId(student.getId())) {
                member.setDisplayName(name);
                memberRepository.save(member);
            }
            imService.updateMessageSenderName(student.getId(), name);
        }

        StudentEntity owner = students.get(0);
        List<Long> memberIds = students.stream().limit(3).map(StudentEntity::getId).toList();

        ChatConversationEntity demoGroup = conversationRepository.findAll().stream()
                .filter(c -> "GROUP".equals(c.getType()))
                .filter(c -> GROUP_TITLE.equals(c.getTitle()) || LEGACY_GROUP_TITLE.equals(c.getTitle()))
                .findFirst()
                .orElse(null);

        boolean needWelcome = false;
        if (demoGroup == null) {
            ConversationSummary group = imService.createGroup(owner.getId(), GROUP_TITLE, memberIds);
            demoGroup = conversationRepository.findById(group.id()).orElseThrow();
            needWelcome = true;
        } else if (LEGACY_GROUP_TITLE.equals(demoGroup.getTitle())) {
            // 从旧演示数据升级：改名并清空旧群历史，避免仍显示“陈老师/软件工程课程群”。
            demoGroup.setTitle(GROUP_TITLE);
            conversationRepository.save(demoGroup);
            imService.resetConversationForDemo(demoGroup.getId());
            needWelcome = true;
        } else if (demoGroup.getLastSeq() == null || demoGroup.getLastSeq() == 0) {
            needWelcome = true;
        }

        if (needWelcome) {
            imService.sendMessage(
                    demoGroup.getId(),
                    owner.getId(),
                    "welcome-" + UUID.randomUUID(),
                    "这是示例群聊，用户A、用户B、用户C 可以在这里实时聊天。"
            );
        }
        log.info("im demo data ready: group={} title={} members={}",
                demoGroup.getId(), demoGroup.getTitle(), memberIds);
    }

    private String uniqueStudentNo(String base) {
        if (studentRepository.findByStudentNo(base).isEmpty()) {
            return base;
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
