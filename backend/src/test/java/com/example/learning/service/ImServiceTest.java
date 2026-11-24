package com.example.learning.service;

import com.example.learning.common.BizException;
import com.example.learning.dto.im.ConversationSummary;
import com.example.learning.dto.im.MessageDto;
import com.example.learning.dto.im.MessagePage;
import com.example.learning.entity.StudentEntity;
import com.example.learning.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(ImService.class)
class ImServiceTest {

    @Autowired
    private ImService imService;

    @Autowired
    private StudentRepository studentRepository;

    private StudentEntity teacher;
    private StudentEntity alice;
    private StudentEntity bob;

    @BeforeEach
    void setUp() {
        teacher = student("demo-001", "陈老师");
        alice = student("demo-002", "李同学");
        bob = student("demo-003", "王同学");
    }

    @Test
    void groupChatAssignsSeqAndIsIdempotent() {
        ConversationSummary group = imService.createGroup(
                teacher.getId(), "软件工程课程群", List.of(alice.getId(), bob.getId()));

        MessageDto first = imService.sendMessage(
                group.id(), teacher.getId(), "client-1", "同学们好");
        MessageDto second = imService.sendMessage(
                group.id(), alice.getId(), "client-2", "老师好");
        MessageDto duplicate = imService.sendMessage(
                group.id(), teacher.getId(), "client-1", "同学们好");

        assertThat(first.seq()).isEqualTo(1L);
        assertThat(second.seq()).isEqualTo(2L);
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(duplicate.seq()).isEqualTo(first.seq());

        List<ConversationSummary> conversations = imService.listConversations(alice.getId());
        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).unreadCount()).isEqualTo(2L);
        assertThat(conversations.get(0).members()).hasSize(3);

        long unreadAfterRead = imService.markRead(group.id(), alice.getId(), 1L);
        assertThat(unreadAfterRead).isEqualTo(1L);

        MessagePage page = imService.getMessages(group.id(), alice.getId(), 1L, 50);
        assertThat(page.messages()).hasSize(1);
        assertThat(page.messages().get(0).content()).isEqualTo("老师好");
    }

    @Test
    void directConversationIsReusedAndProtected() {
        ConversationSummary first = imService.createDirect(teacher.getId(), alice.getId());
        ConversationSummary again = imService.createDirect(teacher.getId(), alice.getId());

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(first.members()).hasSize(2);
        assertThat(first.title()).isEqualTo("李同学");

        assertThatThrownBy(() -> imService.sendMessage(
                first.id(), bob.getId(), "client-x", "我不在会话里"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(403);

        assertThatThrownBy(() -> imService.getMessages(first.id(), bob.getId(), 0, 50))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    private StudentEntity student(String no, String name) {
        StudentEntity student = new StudentEntity();
        student.setStudentNo(no);
        student.setName(name);
        student.setMajor("软件工程");
        return studentRepository.save(student);
    }
}
