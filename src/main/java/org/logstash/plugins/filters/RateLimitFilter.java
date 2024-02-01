package org.logstash.plugins.filters;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.Filter;
import co.elastic.logstash.api.FilterMatchListener;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.PluginConfigSpec;
import co.elastic.logstash.api.PluginHelper;
import com.google.common.util.concurrent.RateLimiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 1.基于guava的RateLimiter实现动态限流
 * 读取指定文本的第一行的值作为限流的阈值，如果转化double失败，则不进行限流
 *
 * 2.同时记录并打印event的数量
 */
@LogstashPlugin(name = "java_rate_limit")
public class RateLimitFilter implements Filter {

    private static final Logger log = LogManager.getLogger(RateLimitFilter.class);

    public static final PluginConfigSpec<String> RATE_PATH = PluginConfigSpec.stringSetting("rate_path");

    public static final PluginConfigSpec<String> COUNT_PATH = PluginConfigSpec.stringSetting("count_path");

    public static final PluginConfigSpec<Long> COUNT_LOG_DELAY_SEC = PluginConfigSpec.numSetting("count_log_delay_sec", 30L);

    public static final ScheduledExecutorService SCHEDULER = new ScheduledThreadPoolExecutor(2, new NamedThreadFactory("rate-limit", true));

    private String id;

    // 限流值的文本路径
    private final String ratePath;

    // 记录数量的文本的路径
    private final String countPath;

    // 打印日志的延时
    private final Long countLogDelaySec;


    private final boolean rateLimiterEnabled;

    private final boolean recordEventCountToFileEnabled;

    // 限流
    private RateLimiter rateLimiter;

    // 上一次的rate限流值
    private double lastRate;

    // event的数量
    private LongAdder eventCounter = new LongAdder();

    // 打印日志的次数
    private LongAdder logCounter = new LongAdder();

    /**
     * Required constructor.
     *
     * @param id            Plugin id
     * @param configuration Logstash Configuration
     * @param context       Logstash Context
     */
    public RateLimitFilter(final String id, final Configuration configuration, final Context context) {
        this.id = id;
        this.ratePath = configuration.get(RATE_PATH);
        this.countPath = configuration.get(COUNT_PATH);
        this.countLogDelaySec = configuration.get(COUNT_LOG_DELAY_SEC);

        this.rateLimiterEnabled = ratePath != null && !ratePath.trim().isEmpty();
        this.recordEventCountToFileEnabled = countPath != null && !countPath.trim().isEmpty();

        if (rateLimiterEnabled) {
            // 每1s刷新一次rateLimiter
            SCHEDULER.scheduleWithFixedDelay(() -> updateRateLimiterIfRateChanged(), 0, 1, TimeUnit.SECONDS);
        }

        log.warn("### Rate limiter enabled:[{}]! ratePath:[{}].", rateLimiterEnabled, ratePath);
        log.warn("### Record event count to file enabled:[{}]! countPath:[{}].", recordEventCountToFileEnabled, countPath);

        // 每1s记录一次eventCount到文本中
        SCHEDULER.scheduleWithFixedDelay(() -> recordEventCount(), 0, 1, TimeUnit.SECONDS);

        // 添加勾子，打印日志
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long currentEventCount = eventCounter.longValue();
            log.info("Logstash will shut down immediately! eventCount:[{}] rate:[{}].", currentEventCount, lastRate);
        }, "rate-limit-shutdown-hook"));
    }


    private void updateRateLimiterIfRateChanged() {
        double rate = -1D;
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(ratePath))) {
            String firstLine = bufferedReader.readLine();
            if (firstLine != null) {
                rate = Double.valueOf(firstLine);
            }
        } catch (Throwable e) {
            log.error("Get rate value failed!", e);
        }

        if (rate != lastRate) {
            if (rate <= 0D) {
                rateLimiter = null;
                log.warn("# Rate is not positive, set RateLimiter to null! lastRate:[{}] rate:[{}] ratePath:[{}].", lastRate, rate, ratePath);
            } else if (rate > 0D) {
                rateLimiter = RateLimiter.create(rate);
                log.warn("# Rate changed, set new RateLimiter! lastRate:[{}] rate:[{}] ratePath:[{}].", lastRate, rate, ratePath);
            }
            lastRate = rate;
        }

    }

    /**
     * 记录Event的数量
     */
    private void recordEventCount() {
        long currentEventCount = eventCounter.longValue();
        logCounter.increment();

        // 每秒一次，根据延时控制打印日志的频率
        if (countLogDelaySec > 0L && logCounter.longValue() % countLogDelaySec == 0L) {
            log.info("Event count:[{}] rate:[{}].", currentEventCount, lastRate);
        }

        // countPatch不为空则记录count到指定的文件中
        if (recordEventCountToFileEnabled) {
            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(countPath))) {
                bufferedWriter.write(String.valueOf(currentEventCount));
                bufferedWriter.flush();
            } catch (Throwable e) {
                log.error("Record eventCount failed!", e);
            }
        }
    }

    @Override
    public Collection<Event> filter(Collection<Event> events, final FilterMatchListener filterMatchListener) {
        if (events == null || events.isEmpty()) {
            return events;
        }
        int size = events.size();
        // 限流
        if (rateLimiter != null) {
            rateLimiter.acquire(size);
        }
        for (Event event : events) {
            filterMatchListener.filterMatched(event);
        }
        // 修改计数器的值
        eventCounter.add(size);
        return events;
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        return PluginHelper.commonFilterSettings(Arrays.asList(RATE_PATH, COUNT_PATH, COUNT_LOG_DELAY_SEC));
    }

    @Override
    public String getId() {
        return id;
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String name;
        private final boolean daemon;
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(0);

        public NamedThreadFactory(String name, boolean daemon) {
            this.name = name;
            this.daemon = daemon;
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, name + "-" + threadNumber.getAndIncrement());
            t.setDaemon(daemon);
            return t;
        }

    }

}
