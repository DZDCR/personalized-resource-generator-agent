package com.example.learning.service;

import com.example.learning.common.BizException;
import com.example.learning.dto.im.*;
import com.example.learning.entity.ChatConversationEntity;
import com.example.learning.entity.ChatMemberEntity;
import com.example.learning.entity.ChatMessageEntity;
import com.example.learning.entity.StudentEntity;
import com.example.learning.repository.ChatConversationRepository;
import com.example.learning.repository.ChatMemberRepository;
import com.example.learning.repository.ChatMessageRepository;
import com.example.learning.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 实时聊天核心服务：会话、成员、消息、已读游标。
 * <p>消息只存一份；私聊/群聊通过成员关系做权限校验。发送时锁定会话行分配 seq，
 * 保证同一会话内严格有序，seq 允许空洞。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final StudentRepository studentRepository;

    public List<DemoUser> listDemoUsers() {
        return studentRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .limit(20)
                .map(s -> new DemoUser(s.getId(), s.getStudentNo(), displayName(s)))
                .toList();
    }

    public boolean studentExists(Long studentId) {
        return studentId != null && studentRepository.existsById(studentId);
    }

    public long countConversations() {
        return conversationRepository.count();
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(Long userId) {
        List<ConversationSummary> result = new ArrayList<>();
        for (ChatMemberEntity membership : memberRepository.findByUserId(userId)) {
            conversationRepository.findById(membership.getConversationId())
                    .ifPresent(conv -> result.add(toSummary(conv, membership)));
        }
        result.sort((a, b) -> {
            Instant ta = a.lastMessageAt();
            Instant tb = b.lastMessageAt();
            if (ta == null && tb == null) return Long.compare(b.id(), a.id());
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return result;
    }

    @Transactional
    public ConversationSummary createDirect(Long userId, Long peerId) {
        if (Objects.equals(userId, peerId)) {
            throw new BizException(400, "不能和自己发起私聊");
        }
        StudentEntity me = requireStudent(userId);
        StudentEntity peer = requireStudent(peerId);

        // 私聊会话唯一：先查找双方已有的 DIRECT 会话，存在则直接复用，避免重复建会话。
        for (ChatMemberEntity membership : memberRepository.findByUserId(userId)) {
            ChatConversationEntity conv = conversationRepository.findById(membership.getConversationId()).orElse(null);
            if (conv == null || !"DIRECT".equals(conv.getType())) continue;
            List<ChatMemberEntity> members = memberRepository.findByConversationId(conv.getId());
            boolean containsPeer = members.stream().anyMatch(m -> Objects.equals(m.getUserId(), peerId));
            if (members.size() == 2 && containsPeer) {
                return toSummary(conv, membership);
            }
        }

        ChatConversationEntity conv = new ChatConversationEntity();
        conv.setType("DIRECT");
        conv.setOwnerId(userId);
        conv.setLastSeq(0L);
        conv = conversationRepository.save(conv);
        addMember(conv.getId(), me, "OWNER");
        addMember(conv.getId(), peer, "MEMBER");
        return toSummary(conv, memberRepository.findByConversationIdAndUserId(conv.getId(), userId).orElseThrow());
    }

    @Transactional
    public ConversationSummary createGroup(Long ownerId, String title, List<Long> memberIds) {
        StudentEntity owner = requireStudent(ownerId);
        String groupTitle = title == null ? "" : title.trim();
        if (groupTitle.isEmpty()) {
            throw new BizException(400, "群聊名称不能为空");
        }

        // 用 LinkedHashSet 去重并保持邀请顺序，同时确保群主一定在成员列表里。
        Set<Long> uniqueIds = new LinkedHashSet<>();
        uniqueIds.add(ownerId);
        if (memberIds != null) {
            for (Long id : memberIds) {
                if (id != null) uniqueIds.add(id);
            }
        }
        if (uniqueIds.size() < 2) {
            throw new BizException(400, "群聊至少需要两名成员");
        }

        ChatConversationEntity conv = new ChatConversationEntity();
        conv.setType("GROUP");
        conv.setTitle(groupTitle);
        conv.setOwnerId(ownerId);
        conv.setLastSeq(0L);
        conv = conversationRepository.save(conv);

        for (Long id : uniqueIds) {
            StudentEntity student = id.equals(ownerId) ? owner : requireStudent(id);
            addMember(conv.getId(), student, id.equals(ownerId) ? "OWNER" : "MEMBER");
        }
        return toSummary(conv, memberRepository.findByConversationIdAndUserId(conv.getId(), ownerId).orElseThrow());
    }

    /**
     * 发送消息。调用方应在事务提交后再广播；REST/WS 共用此方法。
     */
    @Transactional
    public MessageDto sendMessage(Long conversationId, Long senderId, String clientMsgId, String content) {
        if (clientMsgId == null || clientMsgId.isBlank()) {
            throw new BizException(400, "clientMsgId 不能为空");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BizException(400, "消息不能为空");
        }
        if (text.length() > 4000) {
            throw new BizException(400, "消息不能超过 4000 字");
        }

        // 先锁会话，再查幂等键：同一会话内的并发重试会被串行化，避免唯一键冲突。
        ChatConversationEntity conv = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BizException(404, "会话不存在"));
        ChatMemberEntity sender = memberRepository.findByConversationIdAndUserId(conversationId, senderId)
                .orElseThrow(() -> new BizException(403, "你不是该会话成员"));

        // 幂等处理：客户端重连或重试会带相同 clientMsgId，直接返回已落库的消息，避免重复发送。
        Optional<ChatMessageEntity> existing = messageRepository.findBySenderIdAndClientMsgId(senderId, clientMsgId);
        if (existing.isPresent()) {
            ChatMessageEntity message = existing.get();
            if (!Objects.equals(message.getConversationId(), conversationId)) {
                throw new BizException(409, "clientMsgId 已用于其他会话");
            }
            return toDto(message);
        }

        // 递增会话级 seq：同一会话内严格有序，跨会话不要求连续（允许空洞）。
        long seq = (conv.getLastSeq() == null ? 0L : conv.getLastSeq()) + 1;
        Instant now = Instant.now();
        conv.setLastSeq(seq);
        conv.setLastMessagePreview(preview(text));
        conv.setLastMessageAt(now);
        conversationRepository.save(conv);

        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversationId(conversationId);
        message.setSeq(seq);
        message.setSenderId(senderId);
        message.setSenderName(sender.getDisplayName());
        message.setClientMsgId(clientMsgId);
        message.setType("TEXT");
        message.setContent(text);
        message.setCreatedAt(now);
        message = messageRepository.save(message);
        return toDto(message);
    }

    @Transactional(readOnly = true)
    public MessagePage getMessages(Long conversationId, Long userId, long afterSeq, int limit) {
        assertMember(conversationId, userId);
        ChatConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BizException(404, "会话不存在"));
        int size = Math.min(Math.max(limit, 1), 100);

        List<ChatMessageEntity> entities;
        boolean hasMore;
        if (afterSeq > 0) {
            // 增量补拉：断线重连后只取 lastSeq 之后的消息，按 seq 升序返回。
            entities = new ArrayList<>(messageRepository.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(
                    conversationId, afterSeq, PageRequest.of(0, size + 1)));
            hasMore = entities.size() > size;
            if (hasMore) entities = new ArrayList<>(entities.subList(0, size));
        } else {
            // 首屏加载：先按 seq 倒序取最近 N 条，再反转成正序供前端直接渲染。
            entities = new ArrayList<>(messageRepository.findByConversationIdOrderBySeqDesc(
                    conversationId, PageRequest.of(0, size + 1)));
            hasMore = entities.size() > size;
            if (hasMore) entities = new ArrayList<>(entities.subList(0, size));
            Collections.reverse(entities);
        }
        List<MessageDto> messages = entities.stream().map(this::toDto).toList();
        return new MessagePage(messages, conv.getLastSeq(), hasMore);
    }

    @Transactional
    public long markRead(Long conversationId, Long userId, long seq) {
        ChatConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BizException(404, "会话不存在"));
        ChatMemberEntity member = memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BizException(403, "你不是该会话成员"));
        // 已读游标只能前进；且不能超过会话当前最大 seq，防止客户端传入越界值。
        long clamped = Math.max(0, Math.min(seq, conv.getLastSeq() == null ? 0 : conv.getLastSeq()));
        if (clamped > member.getLastReadSeq()) {
            member.setLastReadSeq(clamped);
            memberRepository.save(member);
        }
        return messageRepository.countByConversationIdAndSeqGreaterThan(conversationId, member.getLastReadSeq());
    }

    /**
     * 仅用于演示数据迁移：清空示例群的历史消息并重置游标，
     * 不触碰用户创建的其他会话。
     */
    @Transactional
    public void resetConversationForDemo(Long conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        ChatConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BizException(404, "会话不存在"));
        conv.setLastSeq(0L);
        conv.setLastMessagePreview(null);
        conv.setLastMessageAt(null);
        conversationRepository.save(conv);
        for (ChatMemberEntity member : memberRepository.findByConversationId(conversationId)) {
            member.setLastReadSeq(0L);
            memberRepository.save(member);
        }
    }

    @Transactional
    public int updateMessageSenderName(Long senderId, String senderName) {
        return messageRepository.updateSenderName(senderId, senderName);
    }

    @Transactional(readOnly = true)
    public List<MemberSummary> getMembers(Long conversationId, Long userId) {
        assertMember(conversationId, userId);
        return memberRepository.findByConversationId(conversationId).stream()
                .sorted(Comparator.comparingInt((ChatMemberEntity m) -> "OWNER".equals(m.getRole()) ? 0 : 1)
                        .thenComparing(ChatMemberEntity::getUserId))
                .map(m -> new MemberSummary(m.getUserId(), m.getDisplayName(), m.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> memberIds(Long conversationId) {
        return memberRepository.findByConversationId(conversationId).stream()
                .map(ChatMemberEntity::getUserId)
                .toList();
    }

    public boolean isMember(Long conversationId, Long userId) {
        return memberRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    private ConversationSummary toSummary(ChatConversationEntity conv, ChatMemberEntity me) {
        List<MemberSummary> members = memberRepository.findByConversationId(conv.getId()).stream()
                .sorted(Comparator.comparingInt((ChatMemberEntity m) -> "OWNER".equals(m.getRole()) ? 0 : 1)
                        .thenComparing(ChatMemberEntity::getUserId))
                .map(m -> new MemberSummary(m.getUserId(), m.getDisplayName(), m.getRole()))
                .toList();

        String title = conv.getTitle();
        // 私聊不存标题，展示时取“对方”的显示名；群聊使用创建时填写的群名称。
        if ("DIRECT".equals(conv.getType())) {
            title = members.stream()
                    .filter(m -> !Objects.equals(m.userId(), me.getUserId()))
                    .map(MemberSummary::displayName)
                    .findFirst()
                    .orElse("私聊");
        }
        long unread = messageRepository.countByConversationIdAndSeqGreaterThan(conv.getId(), me.getLastReadSeq());
        return new ConversationSummary(
                conv.getId(),
                conv.getType(),
                title,
                conv.getLastSeq(),
                unread,
                conv.getLastMessagePreview(),
                conv.getLastMessageAt(),
                members
        );
    }

    private void addMember(Long conversationId, StudentEntity student, String role) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setConversationId(conversationId);
        member.setUserId(student.getId());
        member.setDisplayName(displayName(student));
        member.setRole(role);
        member.setLastReadSeq(0L);
        memberRepository.save(member);
    }

    private void assertMember(Long conversationId, Long userId) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BizException(403, "你不是该会话成员");
        }
    }

    private StudentEntity requireStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new BizException(404, "学生不存在: " + studentId));
    }

    private String displayName(StudentEntity student) {
        return student.getName() == null || student.getName().isBlank()
                ? "学生" + student.getId()
                : student.getName();
    }

    private String preview(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120) + "…";
    }

    private MessageDto toDto(ChatMessageEntity message) {
        return new MessageDto(
                message.getId(),
                message.getConversationId(),
                message.getSeq(),
                message.getSenderId(),
                message.getSenderName(),
                message.getType(),
                message.getContent(),
                message.getClientMsgId(),
                message.getCreatedAt()
        );
    }
}
