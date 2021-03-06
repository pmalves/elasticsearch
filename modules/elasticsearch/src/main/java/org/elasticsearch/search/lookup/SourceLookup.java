/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.lookup;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.elasticsearch.ElasticSearchParseException;
import org.elasticsearch.common.compress.lzf.LZF;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.CachedStreamInput;
import org.elasticsearch.common.io.stream.LZFStreamInput;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.mapper.internal.SourceFieldMapper;
import org.elasticsearch.index.mapper.internal.SourceFieldSelector;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author kimchy (shay.banon)
 */
// TODO: If we are processing it in the per hit fetch phase, we cna initialize it with a source if it was loaded..
public class SourceLookup implements Map {

    private IndexReader reader;

    private int docId = -1;

    private Map<String, Object> source;

    public Map<String, Object> source() {
        return source;
    }

    private Map<String, Object> loadSourceIfNeeded() {
        if (source != null) {
            return source;
        }
        XContentParser parser = null;
        try {
            Document doc = reader.document(docId, SourceFieldSelector.INSTANCE);
            Fieldable sourceField = doc.getFieldable(SourceFieldMapper.NAME);
            byte[] source = sourceField.getBinaryValue();
            this.source = sourceAsMap(source, 0, source.length);
        } catch (Exception e) {
            throw new ElasticSearchParseException("failed to parse / load source", e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
        return this.source;
    }

    public static Map<String, Object> sourceAsMap(byte[] bytes, int offset, int length) {
        XContentParser parser = null;
        try {
            if (LZF.isCompressed(bytes, offset, length)) {
                BytesStreamInput siBytes = new BytesStreamInput(bytes, offset, length);
                LZFStreamInput siLzf = CachedStreamInput.cachedLzf(siBytes);
                XContentType contentType = XContentFactory.xContentType(siLzf);
                siLzf.resetToBufferStart();
                parser = XContentFactory.xContent(contentType).createParser(siLzf);
                return parser.map();
            } else {
                parser = XContentFactory.xContent(bytes, offset, length).createParser(bytes, offset, length);
                return parser.map();
            }
        } catch (Exception e) {
            throw new ElasticSearchParseException("Failed to parse source to map", e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    public void setNextReader(IndexReader reader) {
        if (this.reader == reader) { // if we are called with the same reader, don't invalidate source
            return;
        }
        this.reader = reader;
        this.source = null;
        this.docId = -1;
    }

    public void setNextDocId(int docId) {
        if (this.docId == docId) { // if we are called with the same docId, don't invalidate source
            return;
        }
        this.docId = docId;
        this.source = null;
    }

    public void setNextSource(Map<String, Object> source) {
        this.source = source;
    }

    /**
     * Returns the values associated with the path. Those are "low" level values, and it can
     * handle path expression where an array/list is navigated within.
     */
    public List<Object> extractRawValues(String path) {
        return XContentMapValues.extractRawValues(path, loadSourceIfNeeded());
    }

    public Object extractValue(String path) {
        return XContentMapValues.extractValue(path, loadSourceIfNeeded());
    }

    @Override public Object get(Object key) {
        return loadSourceIfNeeded().get(key);
    }

    @Override public int size() {
        return loadSourceIfNeeded().size();
    }

    @Override public boolean isEmpty() {
        return loadSourceIfNeeded().isEmpty();
    }

    @Override public boolean containsKey(Object key) {
        return loadSourceIfNeeded().containsKey(key);
    }

    @Override public boolean containsValue(Object value) {
        return loadSourceIfNeeded().containsValue(value);
    }

    @Override public Set keySet() {
        return loadSourceIfNeeded().keySet();
    }

    @Override public Collection values() {
        return loadSourceIfNeeded().values();
    }

    @Override public Set entrySet() {
        return loadSourceIfNeeded().entrySet();
    }

    @Override public Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override public void putAll(Map m) {
        throw new UnsupportedOperationException();
    }

    @Override public void clear() {
        throw new UnsupportedOperationException();
    }
}
