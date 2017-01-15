package com.birbit.android.jobqueue.test.util;

import com.birbit.android.jobqueue.RunningJobSet;
import com.birbit.android.jobqueue.test.timer.MockTimer;
import com.birbit.android.jobqueue.timer.SystemTimer;
import com.birbit.android.jobqueue.timer.Timer;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class RunningJobSetTest {
    RunningJobSet set;
    @Before
    public void setUp() {
        set = new RunningJobSet(new SystemTimer());
    }

    @Test
    public void testEmpty() {
        assertThat(set.getSafe().size(), is(0));
    }

    @Test
    public void testAdd() {
        set.add("g1");
        assertThat(set.getSafe().iterator().next(), is("g1"));
    }

    @Test
    public void testAddTheSame() {
        set.add("g1");
        set.add("g1");
        assertThat(set.getSafe().iterator().next(), is("g1"));
        assertThat(set.getSafe().size(), is(1));
    }

    @Test
    public void testRemove() {
        set.add("g1");
        set.remove("g1");
        assertThat(set.getSafe().iterator().hasNext(), is(false));
    }

    @Test
    public void testOrder() {
        set.add("a");
        set.add("z");
        set.add("b");
        assertList("a", "b", "z");
    }

    private void assertList(String... items) {
        assertThat(set.getSafe().size(), is(items.length));
        Iterator<String> iterator = set.getSafe().iterator();
        for (int i = 0; i < items.length; i++) {
            assertThat(iterator.next(), is(items[i]));
        }
    }

    @Test
    public void testAddWithTimeout() {
        MockTimer timer = new MockTimer();
        set = new RunningJobSet(timer);
        set.addGroupUntil("g1", 10L);
        timer.setNow(5);
        assertList("g1");
        timer.setNow(11);
        assertList();
        timer.setNow(3);
        assertList(); // should've pruned the list
    }

    @Test
    public void testAddSameGroupTwiceWithTimeout() {
        MockTimer timer = new MockTimer();
        set = new RunningJobSet(timer);
        set.addGroupUntil("g1", 10L);
        set.addGroupUntil("g1", 12L);
        timer.setNow(5);
        assertList("g1");
        timer.setNow(11);
        assertList("g1");
        timer.setNow(13);
        assertList();
    }

    @Test
    public void testAddMultipleGroupTimeouts() {
        MockTimer timer = new MockTimer();
        set = new RunningJobSet(timer);
        set.addGroupUntil("g1", 10L);
        set.addGroupUntil("g2", 20L);
        timer.setNow(5);
        assertList("g1", "g2");
        timer.setNow(11);
        assertList("g2");
        timer.setNow(21);
        assertList();
    }
}
