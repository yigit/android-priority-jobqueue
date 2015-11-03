package com.path.android.jobqueue.test.util;

import com.path.android.jobqueue.RunningJobSet;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class RunningJobSetTest {
    RunningJobSet set;
    @Before
    public void setUp() {
        set = new RunningJobSet();
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
        final AtomicLong time = new AtomicLong();
        set = new RunningJobSet() {
            @Override
            protected long now() {
                return time.get();
            }
        };
        set.addGroupUntil("g1", 10L);
        time.set(5);
        assertList("g1");
        time.set(11);
        assertList();
        time.set(3);
        assertList(); // should've pruned the list
    }

    @Test
    public void testAddSameGroupTwiceWithTimeout() {
        final AtomicLong time = new AtomicLong();
        set = new RunningJobSet() {
            @Override
            protected long now() {
                return time.get();
            }
        };
        set.addGroupUntil("g1", 10L);
        set.addGroupUntil("g1", 12L);
        time.set(5);
        assertList("g1");
        time.set(11);
        assertList("g1");
        time.set(13);
        assertList();
    }

    @Test
    public void testAddMultipleGroupTimeouts() {
        final AtomicLong time = new AtomicLong();
        set = new RunningJobSet() {
            @Override
            protected long now() {
                return time.get();
            }
        };
        set.addGroupUntil("g1", 10L);
        set.addGroupUntil("g2", 20L);
        time.set(5);
        assertList("g1", "g2");
        time.set(11);
        assertList("g2");
        time.set(21);
        assertList();
    }
}
