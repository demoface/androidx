/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.arch.background.workmanager.model;

import android.arch.persistence.room.TypeConverter;
import android.net.Uri;
import android.support.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Stores a set of {@link ContentUriTrigger}s
 */

public class ContentUriTriggers implements Iterable<ContentUriTriggers.ContentUriTrigger> {
    private final Set<ContentUriTrigger> mTriggers = new HashSet<>();

    /**
     * Add a Content {@link Uri} to observe
     * @param uri {@link Uri} to observe
     * @param triggerForDescendants {@code true} if any changes in descendants cause this
     *                              {@link WorkSpec} to run
     */
    public void add(Uri uri, boolean triggerForDescendants) {
        ContentUriTrigger trigger = new ContentUriTrigger(uri, triggerForDescendants);
        mTriggers.add(trigger);
    }

    @NonNull
    @Override
    public Iterator<ContentUriTrigger> iterator() {
        return mTriggers.iterator();
    }

    /**
     * @return number of {@link ContentUriTrigger} objects
     */
    public int size() {
        return mTriggers.size();
    }

    /**
     * Converts a list of {@link ContentUriTrigger}s to byte array representation
     * @param triggers the list of {@link ContentUriTrigger}s to convert
     * @return corresponding byte array representation
     */
    @TypeConverter
    public static byte[] toByteArray(ContentUriTriggers triggers) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = null;
        try {
            objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeInt(triggers.size());
            for (ContentUriTrigger trigger : triggers) {
                objectOutputStream.writeUTF(trigger.getUri().toString());
                objectOutputStream.writeBoolean(trigger.isTriggerForDescendants());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return outputStream.toByteArray();
    }

    /**
     * Converts a byte array to list of {@link ContentUriTrigger}s
     * @param bytes byte array representation to convert
     * @return list of {@link ContentUriTrigger}s
     */
    @TypeConverter
    public static ContentUriTriggers fromByteArray(byte[] bytes) {
        ContentUriTriggers triggers = new ContentUriTriggers();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objectInputStream = null;
        try {
            objectInputStream = new ObjectInputStream(inputStream);
            for (int i = objectInputStream.readInt(); i > 0; i--) {
                Uri uri = Uri.parse(objectInputStream.readUTF());
                boolean triggersForDescendants = objectInputStream.readBoolean();
                triggers.add(uri, triggersForDescendants);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (objectInputStream != null) {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return triggers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ContentUriTriggers that = (ContentUriTriggers) o;

        return mTriggers.equals(that.mTriggers);
    }

    @Override
    public int hashCode() {
        return mTriggers.hashCode();
    }

    /**
     * Defines a content {@link Uri} trigger for a {@link WorkSpec}
     */

    public static class ContentUriTrigger {
        @NonNull
        private final Uri mUri;
        private final boolean mTriggerForDescendants;

        public ContentUriTrigger(@NonNull Uri uri,
                                 boolean triggerForDescendants) {
            mUri = uri;
            mTriggerForDescendants = triggerForDescendants;
        }

        @NonNull
        public Uri getUri() {
            return mUri;
        }

        public boolean isTriggerForDescendants() {
            return mTriggerForDescendants;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ContentUriTrigger trigger = (ContentUriTrigger) o;

            return mTriggerForDescendants == trigger.mTriggerForDescendants
                    && mUri.equals(trigger.mUri);
        }

        @Override
        public int hashCode() {
            int result = mUri.hashCode();
            result = 31 * result + (mTriggerForDescendants ? 1 : 0);
            return result;
        }
    }
}
