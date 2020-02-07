package com.birbit.android.jobqueue.config;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import android.content.Context;

import java.util.UUID;

@RunWith(JUnit4.class)
public class ConfigurationBuilderTest {
    Configuration.Builder builder;
    @Before
    public void init() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getApplicationContext()).thenReturn(context);
        builder = new Configuration.Builder(context);
    }

    @Test(expected = IllegalArgumentException.class)
    public void idWithSpaces() {
        testId("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullId() {
        testId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void idWithSpace() {
        testId("hello world");
    }

    @Test(expected = IllegalArgumentException.class)
    public void idWithBadChars() {
        testId("hello~world");
    }

    @Test(expected = IllegalArgumentException.class)
    public void idWithBadChars2() {
        testId("hello?world");
    }

    @Test
    public void goodId() {
        testId("blah");
    }

    @Test
    public void goodId2() {
        testId("blah123");
    }

    @Test
    public void goodId3() {
        testId("blah_123");
    }

    @Test
    public void goodId4() {
        testId("blah-123");
    }

    @Test
    public void goodUUID() {
        testId(UUID.randomUUID().toString());
    }

    private void testId(String id) {
        builder.id(id).build();
    }
}
