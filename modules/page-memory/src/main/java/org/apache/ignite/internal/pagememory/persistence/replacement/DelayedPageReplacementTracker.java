/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.pagememory.persistence.replacement;

import static org.apache.ignite.internal.pagememory.persistence.PersistentPageMemory.segmentIndex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.internal.logger.IgniteLogger;
import org.apache.ignite.internal.pagememory.FullPageId;
import org.apache.ignite.internal.pagememory.persistence.WriteDirtyPage;

/**
 * Delayed page writes tracker. Provides delayed write implementations and allows to check if page is actually being written to page store.
 */
public class DelayedPageReplacementTracker {
    /** Page size. */
    private final int pageSize;

    /** Flush dirty page real implementation. */
    private final WriteDirtyPage flushDirtyPage;

    /** Logger. */
    private final IgniteLogger log;

    /** Lock stripes for pages read protection. */
    private final Stripe[] stripes;

    /** Byte buffer thread local. */
    private final ThreadLocal<ByteBuffer> byteBufThreadLoc = new ThreadLocal<>() {
        /** {@inheritDoc} */
        @Override
        protected ByteBuffer initialValue() {
            ByteBuffer buf = ByteBuffer.allocateDirect(pageSize);

            buf.order(ByteOrder.nativeOrder());

            return buf;
        }
    };

    /**
     * Dirty page write for replacement operations thread local. Because page write {@link DelayedDirtyPageWrite} is stateful and not
     * thread safe, this thread local protects from GC pressure on pages replacement. <br> Map is used instead of build-in thread local to
     * allow GC to remove delayed writers for alive threads after node stop.
     */
    private final Map<Long, DelayedDirtyPageWrite> delayedPageWriteThreadLocMap = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param pageSize Page size.
     * @param flushDirtyPage Flush dirty page.
     * @param log Logger.
     * @param segmentCnt Segments count.
     */
    public DelayedPageReplacementTracker(
            // TODO: IGNITE-17017 Move to common config
            int pageSize,
            WriteDirtyPage flushDirtyPage,
            IgniteLogger log,
            int segmentCnt
    ) {
        this.pageSize = pageSize;
        this.flushDirtyPage = flushDirtyPage;
        this.log = log;

        stripes = new Stripe[segmentCnt];

        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new Stripe();
        }
    }

    /**
     * Returns delayed page write implementation, finish method to be called to actually write page.
     */
    public DelayedDirtyPageWrite delayedPageWrite() {
        return delayedPageWriteThreadLocMap.computeIfAbsent(Thread.currentThread().getId(),
                id -> new DelayedDirtyPageWrite(flushDirtyPage, byteBufThreadLoc, pageSize, this));
    }

    /**
     * Returns stripe related to current page identifier.
     *
     * @param id Full page ID.
     */
    private Stripe stripe(FullPageId id) {
        int segmentIdx = segmentIndex(id.groupId(), id.pageId(), stripes.length);

        return stripes[segmentIdx];
    }

    /**
     * Locks the page.
     *
     * @param id Full page ID to lock from read.
     */
    public void lock(FullPageId id) {
        stripe(id).lock(id);
    }

    /**
     * Method is returned when page is available to be loaded from store, or waits for replacement finish.
     *
     * @param id full page ID to be loaded from store.
     */
    public void waitUnlock(FullPageId id) {
        stripe(id).waitUnlock(id);
    }

    /**
     * Unlocks the page.
     *
     * @param id Full page ID, which write has been finished, it is available for reading.
     */
    public void unlock(FullPageId id) {
        stripe(id).unlock(id);
    }

    /**
     * Stripe for locking pages from reading from store in parallel with not finished write.
     */
    private class Stripe {
        /**
         * Page IDs which are locked for reading from store. Page content is being written right now. guarded by collection object monitor.
         */
        private final Collection<FullPageId> locked = new HashSet<>(Runtime.getRuntime().availableProcessors() * 2);

        /**
         * Has locked pages, flag for fast check if there are some pages, what were replaced and is being written. Write to field is guarded
         * by {@link #locked} monitor.
         */
        private volatile boolean hasLockedPages;

        /**
         * Locks the page.
         *
         * @param id full page ID to lock from read
         */
        public void lock(FullPageId id) {
            synchronized (locked) {
                hasLockedPages = true;

                boolean add = locked.add(id);

                assert add : "Double locking of page for replacement is not possible: " + id;
            }
        }

        /**
         * Method is returned when page is available to be loaded from store, or waits for replacement finish.
         *
         * @param id Full page ID to be loaded from store.
         */
        public void waitUnlock(FullPageId id) {
            if (!hasLockedPages) {
                return;
            }

            synchronized (locked) {
                if (!hasLockedPages) {
                    return;
                }

                boolean interrupted = false;

                while (locked.contains(id)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found replaced page which is being written to page store, wait for finish replacement [id={}]", id);
                    }

                    try {
                        // Uninterruptable wait.
                        locked.wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }

                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * Unlocks the page.
         *
         * @param id Full page ID, which write has been finished, it is available for reading.
         */
        public void unlock(FullPageId id) {
            synchronized (locked) {
                boolean rmv = locked.remove(id);

                assert rmv : "Unlocking page ID never locked, id: " + id;

                if (locked.isEmpty()) {
                    hasLockedPages = false;
                }

                locked.notifyAll();
            }
        }
    }
}
