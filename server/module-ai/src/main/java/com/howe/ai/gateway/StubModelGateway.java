package com.howe.ai.gateway;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.howe.ai.contract.ModelDelta;
import com.howe.ai.contract.ModelGateway;
import com.howe.ai.contract.ModelRequest;
import com.howe.ai.contract.ModelResult;
import com.howe.ai.contract.ModelStreamResult;
import com.howe.ai.contract.ProviderErrorCategory;

/**
 * 可脚本化的 Stub Provider，不执行网络请求。
 *
 * <p>Worker 与事件流可能并发取用同一个 Stub，队列操作必须同步，
 * 否则并发下会出现同一条脚本响应被消费两次或漏消费。</p>
 */
@Service
public class StubModelGateway implements ModelGateway {
    private final Deque<StubResponse> responses = new ArrayDeque<>();

    public synchronized void enqueue(StubResponse response) {
        responses.add(Objects.requireNonNull(response, "Stub 响应不能为空"));
    }

    /** 尚未被消费的脚本内容，用于断言某个渠道确实没有被调用。 */
    public synchronized List<String> remaining() {
        return responses.stream().flatMap(response -> response.deltas().stream())
            .map(ModelDelta::content).toList();
    }

    public ModelStreamResult stream(ModelRequest request) {
        Objects.requireNonNull(request, "模型请求不能为空");
        StubResponse response;
        synchronized (this) {
            response = responses.pollFirst();
        }
        if (response == null) return new ModelStreamResult(List.of(), ProviderErrorCategory.UNKNOWN, "stub-empty");
        return new ModelStreamResult(response.deltas(), response.errorCategory(), response.requestId(),
            response.partial(), response.usage(), response.retryAfterSeconds());
    }
}
