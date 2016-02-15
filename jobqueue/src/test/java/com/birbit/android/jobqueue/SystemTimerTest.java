package com.birbit.android.jobqueue;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.timer.SystemTimer;

import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.annotation.SuppressLint;

import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class SystemTimerTest {
    @SuppressLint({"DIRECT_TIME_ACCESS","SLEEP_IN_CODE"})
    @Test
    public void testNow() throws Throwable {
        SystemTimer timer = new SystemTimer();
        //noinspection DIRECT_TIME_ACCESS
        long startNs = System.nanoTime();
        //noinspection DIRECT_TIME_ACCESS
        long start = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        //noinspection SLEEP_IN_CODE
        Thread.sleep(2000);
        assertReasonable(timer.nanoTime(), System.nanoTime() - startNs + start);
        SystemTimer timer2 = new SystemTimer();
        assertReasonable(timer2.nanoTime(), System.nanoTime() - startNs + start);
    }

    public void assertReasonable(long value, long expected) {
        long deviation = JobManager.NS_PER_MS * 500;
        MatcherAssert.assertThat("expected value should be in range", value,
                new Range(expected - deviation, expected + deviation));
    }

    static class Range extends BaseMatcher<Long> {
        final long min, max;
        public Range(long min, long max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean matches(Object item) {
            long value = (long) item;
            return value >= min && value <= max;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("should be between").appendValue(min).appendText(" and ")
                    .appendValue(max);
        }
    }

}
