package com.examsystem.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/** 捕获项目日志；在请求线程内冻结 MDC，避免测试读取时丢失请求上下文。 */
public final class LogCapture implements AutoCloseable {
    private final Logger logger = (Logger) LoggerFactory.getLogger("com.examsystem");
    private final Level previousLevel = logger.getLevel();
    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final AppenderBase<ILoggingEvent> appender =
            new AppenderBase<>() {
                @Override
                protected void append(ILoggingEvent event) {
                    event.prepareForDeferredProcessing();
                    events.add(event);
                }
            };

    public LogCapture() {
        logger.setLevel(Level.DEBUG);
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }
}
