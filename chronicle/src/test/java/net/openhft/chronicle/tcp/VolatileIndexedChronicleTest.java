/*
 * Copyright 2014 Higher Frequency Trading
 * <p/>
 * http://www.higherfrequencytrading.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.tcp;


import net.openhft.lang.model.constraints.NotNull;
import net.openhft.lang.io.Bytes;
import net.openhft.lang.io.serialization.BytesMarshaller;
import net.openhft.lang.io.serialization.BytesMarshallable;
import net.openhft.chronicle.Chronicle;
import net.openhft.chronicle.ExcerptAppender;
import net.openhft.chronicle.ExcerptTailer;
import net.openhft.chronicle.IndexedChronicle;

import org.junit.Test;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class VolatileIndexedChronicleTest extends VolatileChronicleTestBase {

    @Test
    public void testIndexedVolatileSink_001() throws Exception {
        final int port = BASE_PORT + 101;
        final String basePathSource = getIndexedTestPath("-source");
        final Chronicle source = indexedChronicleSource(basePathSource, port);
        final Chronicle sink = volatileChronicleSink("localhost", port);

        final int items = 1000000;
        final ExcerptAppender appender = source.createAppender();

        try {
            for (long i = 1; i <= items; i++) {
                appender.startExcerpt(8);
                appender.writeLong(i);
                appender.finish();
            }

            appender.close();

            final ExcerptTailer tailer1 = sink.createTailer().toStart();
            assertEquals(-1,tailer1.index());

            for (long i = 1; i <= items; i++) {
                assertTrue(tailer1.nextIndex());
                assertEquals(i - 1, tailer1.index());
                assertEquals(i, tailer1.readLong());
                tailer1.finish();
            }

            assertFalse(tailer1.nextIndex());
            tailer1.close();

            final ExcerptTailer tailer2 = sink.createTailer().toEnd();
            assertEquals(items - 1, tailer2.index());
            assertEquals(items, tailer2.readLong());
            assertFalse(tailer2.nextIndex());
            tailer2.close();

            sink.close();
            sink.clear();
        } finally {
            source.close();
            source.clear();
        }
    }

    @Test
    public void testIndexedVolatileSink_002() throws Exception {
        final int port = BASE_PORT + 102;
        final String basePathSource = getIndexedTestPath("-source");
        final Chronicle source = indexedChronicleSource(basePathSource, port);
        final Chronicle sink = volatileChronicleSink("localhost", port);

        try {
            final ExcerptAppender appender = source.createAppender();
            appender.startExcerpt(8);
            appender.writeLong(1);
            appender.finish();
            appender.startExcerpt(8);
            appender.writeLong(2);
            appender.finish();

            final ExcerptTailer tailer = sink.createTailer().toEnd();
            assertFalse(tailer.nextIndex());

            appender.startExcerpt(8);
            appender.writeLong(3);
            appender.finish();

            while(!tailer.nextIndex());

            assertEquals(2, tailer.index());
            assertEquals(3, tailer.readLong());
            tailer.finish();
            tailer.close();

            appender.close();

            sink.close();
            sink.clear();
        } finally {
            source.close();
            source.clear();
        }
    }

    @Test
    public void testIndexedVolatileSink_003() throws Exception {
        final int port = BASE_PORT + 103;
        final String basePathSource = getIndexedTestPath("-source");

        final Chronicle source = indexedChronicleSource(basePathSource, port);
        final Chronicle sink = volatileChronicleSink("localhost", port);

        final int items = 1000000;
        final ExcerptAppender appender = source.createAppender();

        try {
            for (int i = 0; i <= items; i++) {
                appender.startExcerpt(8);
                appender.writeLong(i);
                appender.finish();
            }

            appender.close();

            final ExcerptTailer tailer = sink.createTailer();
            final Random r = new Random();

            for(int i=0;i<1000;i++) {
                int index = r.nextInt(items);

                assertTrue(tailer.index(index));
                assertEquals(index, tailer.index());
                assertEquals(index, tailer.readLong());

                tailer.finish();
            }

            tailer.close();

            sink.close();
            sink.clear();
        } finally {
            source.close();
            source.clear();
        }
    }

    @Test
    public void testIndexedVolatileSink_004() throws Exception {
        final int port = BASE_PORT + 104;
        final int tailers = 4;
        final int items = 1000000;
        final String basePathSource = getIndexedTestPath("-source");
        final Chronicle source = indexedChronicleSource(basePathSource, port);
        final Chronicle sink = volatileChronicleSink("localhost", port);
        final ExecutorService executor = Executors.newFixedThreadPool(tailers);

        try {

            for(int i=0;i<tailers;i++) {
                executor.submit(new Runnable() {
                    public void run() {
                        try {
                            final ExcerptTailer tailer = sink.createTailer().toStart();
                            for (int i = 0; i < items; ) {
                                if (tailer.nextIndex()) {
                                    assertEquals(i, tailer.index());
                                    assertEquals(i, tailer.readLong());
                                    tailer.finish();

                                    i++;
                                }
                            }

                            tailer.close();
                        }
                        catch (Exception e) {
                        }
                    }
                });
            }

            Thread.sleep(100);

            final ExcerptAppender appender = source.createAppender();

            for (int i=0; i<items; i++) {
                appender.startExcerpt(8);
                appender.writeLong(i);
                appender.finish();
            }

            appender.close();

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            sink.close();
            sink.clear();
        } finally {
            source.close();
            source.clear();
        }
    }

    @Test
    public void testIndexedVolatileSink_005() throws Exception {
        final int port = BASE_PORT + 105;
        final String basePathSource = getIndexedTestPath("-source");
        final Chronicle source = indexedChronicleSource(basePathSource, port);
        final Chronicle sink = volatileChronicleSink("localhost", port);

        final int items = 1000;
        final ExcerptAppender appender = source.createAppender();
        final ExcerptTailer tailer = sink.createTailer();

        try {
            for (int i = 0; i < items; i++) {
                appender.startExcerpt(8);
                appender.writeLong(i);
                appender.finish();

                assertTrue(tailer.index(i));
                assertEquals(i, tailer.readLong());
                tailer.finish();
            }

            appender.close();
            tailer.close();

            sink.close();
            sink.clear();
        } finally {
            source.close();
            source.clear();
        }
    }

    @Test
    public void testIndexedVolatileSink_006() throws Exception {
        final int port = BASE_PORT + 106;
        final String basePathSource = getIndexedTestPath("-source");
        final Chronicle source = indexedChronicleSource(basePathSource, port);
        final Chronicle sink = volatileChronicleSink("localhost", port);

        final int items = 1000000;
        final ExcerptAppender appender = source.createAppender();

        try {
            for (int i=1; i <= items; i++) {
                appender.startExcerpt(8);
                appender.writeLong(i);
                appender.finish();
            }

            appender.close();

            final ExcerptTailer tailer1 = sink.createTailer().toStart();
            assertEquals(-1,tailer1.index());
            assertTrue(tailer1.nextIndex());
            assertEquals(0, tailer1.index());
            assertEquals(1, tailer1.readLong());
            tailer1.finish();
            tailer1.close();

            final ExcerptTailer tailer2 = sink.createTailer().toEnd();
            assertEquals(items - 1, tailer2.index());
            assertEquals(items, tailer2.readLong());
            tailer2.finish();
            tailer2.close();

            sink.close();
            sink.clear();
        } finally {
            source.close();
            source.clear();
        }

    }

    // *************************************************************************
    // JIRA
    // *************************************************************************

    /*
     * https://higherfrequencytrading.atlassian.net/browse/CHRON-74
     */
    @Test
    public void testIndexedJiraChron74() throws Exception {
        final int port = BASE_PORT + 107;
        final String basePathSource = getIndexedTestPath("-source");
        final Chronicle source = indexedChronicleSource(basePathSource, port);

        testJiraChron74(port, source);
    }

    /*
     * https://higherfrequencytrading.atlassian.net/browse/CHRON-75
     */
    @Test
    public void testIndexedJiraChron75() throws Exception {
        final int port = BASE_PORT + 108;
        final String basePathSource = getIndexedTestPath("-source");
        final Chronicle source = indexedChronicleSource(basePathSource, port);

        testJiraChron75(port, source);
    }
}
