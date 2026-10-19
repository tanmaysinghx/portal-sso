package com.tanmaysinghx.portalsso.mail.service;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The thread pool notification mail is sent on.
 *
 * <h2>Why notifications must not be sent on the request thread</h2>
 *
 * <p>A welcome or new-device message is triggered by a sign-in. Sending it inline would add an SMTP
 * connect, TLS handshake and round trip — a second or more against a remote relay, and up to the
 * full timeout against a broken one — to the redirect the user is waiting on. Authentication
 * latency is not the place to pay for a notification nobody is blocked on.
 *
 * <p>The pool is small and the queue bounded on purpose. If mail is backing up, the right outcome
 * is to drop notifications with a warning in the log, not to accumulate an unbounded backlog of
 * them in heap — and the rejection policy is deliberately <em>not</em> {@code CallerRuns}, which
 * would quietly push the work back onto the sign-in thread and reintroduce exactly the latency this
 * exists to avoid.
 */
@Configuration
@EnableAsync
public class MailAsyncConfig {

    public static final String EXECUTOR = "portalMailExecutor";

    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor portalMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("portal-mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Let in-flight notifications finish on shutdown, but never hold the JVM open for them.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
