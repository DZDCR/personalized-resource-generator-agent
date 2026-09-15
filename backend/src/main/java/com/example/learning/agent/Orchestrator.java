package com.example.learning.agent;

import com.example.learning.agent.product.*;
import com.example.learning.common.BizException;
import com.example.learning.config.TaskProgressBroadcaster;
import com.example.learning.dto.request.ResourceGenerateRequest;
import com.example.learning.dto.response.AgentMessage;
import com.example.learning.dto.response.GenerateTaskResponse;
import com.example.learning.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 多智能体协调器。
 *
 * <p>编排流程（串并行混合）：
 * <pre>
 *   Designer.解析蓝图
 *     ├─ 并行：DocWriter / Mindmap / Quiz / Reading（相互独立）
 *     ├─ 串行：Script 依赖 Doc 大纲；Coder 依赖蓝图难度
 *     └─ 聚合 + 质量校验 → 持久化到 ResourceService
 * </pre>
 *
 * <p>并发基于虚拟线程执行器（见 AsyncConfig#resourceExecutor），
 * Agent 之间不直接通信，通过蓝图/产物依赖关系解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private final DesignerAgent designer;
    private final DocWriterAgent writer;
    private final MindmapAgent mapper;
    private final QuizAgent quiz;
    private final ReadingAgent reader;
    private final ScriptAgent script;
    private final CoderAgent coder;
    private final ResourceService resourceService;
    private final Executor resourceExecutor;
    private final TaskProgressBroadcaster progressBroadcaster;

    public GenerateTaskResponse start(ResourceGenerateRequest req) {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture.supplyAsync(() -> run(taskId, req), resourceExecutor)
                .exceptionally(e -> {
                    log.error("task {} failed", taskId, e);
                    return null;
                });
        return new GenerateTaskResponse(taskId, "RUNNING", "资源生成已提交");
    }

    private List<AgentMessage> run(String taskId, ResourceGenerateRequest req) {
        List<AgentMessage> progress = new ArrayList<>();
        String topic = req.topic();
        Long studentId = Long.parseLong(req.studentId());

        // Step1 设计蓝图
        progress.add(AgentMessage.of(taskId, designer.name(), "RUNNING", topic, "解析需求与画像", 0));
        broadcast(taskId, progress.get(0));
        Optional<ResourceBlueprint> specOpt = designer.parse(topic, resourceService.loadProfileJson(studentId));
        if (specOpt.isEmpty()) {
            AgentMessage fail = AgentMessage.of(taskId, designer.name(), "FAILED", topic, "蓝图生成失败", 0);
            broadcast(taskId, fail);
            progress.add(fail);
            return progress;
        }
        ResourceBlueprint spec = specOpt.get();
        AgentMessage specDone = AgentMessage.of(taskId, designer.name(), "DONE", spec.topic(), "蓝图就绪", 0);
        broadcast(taskId, specDone);
        progress.add(specDone);

        // Step2 并行生成四路独立资源
        CompletableFuture<Optional<?>> docFuture = submit(progress, taskId, writer.name(), spec.topic(), () -> writer.generate(spec));
        CompletableFuture<Optional<?>> mapFuture = submit(progress, taskId, mapper.name(), spec.topic(), () -> mapper.generate(spec));
        CompletableFuture<Optional<?>> quizFuture = submit(progress, taskId, quiz.name(), spec.topic(), () -> quiz.generate(spec));
        CompletableFuture<Optional<?>> readFuture = submit(progress, taskId, reader.name(), spec.topic(), () -> reader.generate(spec));
        CompletableFuture.allOf(docFuture, mapFuture, quizFuture, readFuture).join();

        Optional<CourseDoc> docOpt = typed(docFuture, CourseDoc.class);
        Optional<MindmapData> mapOpt = typed(mapFuture, MindmapData.class);
        Optional<QuizSet> quizOpt = typed(quizFuture, QuizSet.class);
        Optional<ReadingMaterial> readOpt = typed(readFuture, ReadingMaterial.class);

        // Step3 串行依赖：脚本依赖文档大纲
        Optional<VideoScript> scriptOpt = docOpt
                .filter(d -> d.markdown() != null && !d.markdown().isBlank())
                .flatMap(d -> script.generate(spec, outlineOf(d)));
        Optional<CodeCaseSet> codeOpt = coder.generate(spec);

        // Step4 聚合校验 + 持久化
        ResourceBundle bundle = new ResourceBundle(
                docOpt.orElse(null), mapOpt.orElse(null), quizOpt.orElse(null),
                readOpt.orElse(null), scriptOpt.orElse(null), codeOpt.orElse(null));
        resourceService.save(bundle, spec, studentId, taskId);

        AgentMessage allDone = AgentMessage.of(taskId, "orchestrator", "DONE", spec.topic(), "全部资源已入库", 0);
        broadcast(taskId, allDone);
        progress.add(allDone);
        return progress;
    }

    private CompletableFuture<Optional<?>> submit(List<AgentMessage> progress, String taskId,
                                                  String agentName, String topic,
                                                  Supplier<Optional<?>> action) {
        return CompletableFuture.supplyAsync(() -> {
            AgentMessage running = AgentMessage.of(taskId, agentName, "RUNNING", topic, "开始生成", 0);
            broadcast(taskId, running);
            progress.add(running);
            Optional<?> result;
            try {
                result = action.get();
            } catch (Exception e) {
                log.warn("agent[{}] failed on {}", agentName, topic, e);
                result = Optional.empty();
            }
            AgentMessage done = AgentMessage.of(taskId, agentName,
                    result.isPresent() ? "DONE" : "FAILED", topic,
                    result.isPresent() ? "生成完成" : "生成失败", 0);
            broadcast(taskId, done);
            progress.add(done);
            return result;
        }, resourceExecutor);
    }

    private void broadcast(String taskId, AgentMessage message) {
        try {
            progressBroadcaster.broadcast(taskId, message);
        } catch (Exception e) {
            log.warn("broadcast failed for task {}", taskId);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> typed(CompletableFuture<Optional<?>> future, Class<T> type) {
        return future.join().map(o -> type.isInstance(o) ? type.cast(o) : null);
    }

    private String outlineOf(CourseDoc doc) {
        return doc.markdown().substring(0, Math.min(doc.markdown().length(), 500));
    }
}