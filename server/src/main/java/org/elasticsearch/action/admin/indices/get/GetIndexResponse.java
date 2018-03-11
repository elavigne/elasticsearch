/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.get;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * A response for a delete index action.
 */
public class GetIndexResponse extends ActionResponse {

    private static final ParseField ALIASES = new ParseField("aliases");
    private static final ParseField MAPPINGS = new ParseField("mappings");
    private static final ParseField SETTINGS = new ParseField("settings");

    private ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings = ImmutableOpenMap.of();
    private ImmutableOpenMap<String, List<AliasMetaData>> aliases = ImmutableOpenMap.of();
    private ImmutableOpenMap<String, Settings> settings = ImmutableOpenMap.of();
    private String[] indices;

    GetIndexResponse(String[] indices,
            ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings,
            ImmutableOpenMap<String, List<AliasMetaData>> aliases, ImmutableOpenMap<String, Settings> settings) {
        this.indices = indices;
        if (mappings != null) {
            this.mappings = mappings;
        }
        if (aliases != null) {
            this.aliases = aliases;
        }
        if (settings != null) {
            this.settings = settings;
        }
    }

    GetIndexResponse() {
    }

    public String[] indices() {
        return indices;
    }

    public String[] getIndices() {
        return indices();
    }

    public ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings() {
        return mappings;
    }

    public ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> getMappings() {
        return mappings();
    }

    public ImmutableOpenMap<String, List<AliasMetaData>> aliases() {
        return aliases;
    }

    public ImmutableOpenMap<String, List<AliasMetaData>> getAliases() {
        return aliases();
    }

    public ImmutableOpenMap<String, Settings> settings() {
        return settings;
    }

    public ImmutableOpenMap<String, Settings> getSettings() {
        return settings();
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        this.indices = in.readStringArray();
        int mappingsSize = in.readVInt();
        ImmutableOpenMap.Builder<String, ImmutableOpenMap<String, MappingMetaData>> mappingsMapBuilder = ImmutableOpenMap.builder();
        for (int i = 0; i < mappingsSize; i++) {
            String key = in.readString();
            int valueSize = in.readVInt();
            ImmutableOpenMap.Builder<String, MappingMetaData> mappingEntryBuilder = ImmutableOpenMap.builder();
            for (int j = 0; j < valueSize; j++) {
                mappingEntryBuilder.put(in.readString(), new MappingMetaData(in));
            }
            mappingsMapBuilder.put(key, mappingEntryBuilder.build());
        }
        mappings = mappingsMapBuilder.build();
        int aliasesSize = in.readVInt();
        ImmutableOpenMap.Builder<String, List<AliasMetaData>> aliasesMapBuilder = ImmutableOpenMap.builder();
        for (int i = 0; i < aliasesSize; i++) {
            String key = in.readString();
            int valueSize = in.readVInt();
            List<AliasMetaData> aliasEntryBuilder = new ArrayList<>(valueSize);
            for (int j = 0; j < valueSize; j++) {
                aliasEntryBuilder.add(new AliasMetaData(in));
            }
            aliasesMapBuilder.put(key, Collections.unmodifiableList(aliasEntryBuilder));
        }
        aliases = aliasesMapBuilder.build();
        int settingsSize = in.readVInt();
        ImmutableOpenMap.Builder<String, Settings> settingsMapBuilder = ImmutableOpenMap.builder();
        for (int i = 0; i < settingsSize; i++) {
            String key = in.readString();
            settingsMapBuilder.put(key, Settings.readSettingsFromStream(in));
        }
        settings = settingsMapBuilder.build();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringArray(indices);
        out.writeVInt(mappings.size());
        for (ObjectObjectCursor<String, ImmutableOpenMap<String, MappingMetaData>> indexEntry : mappings) {
            out.writeString(indexEntry.key);
            out.writeVInt(indexEntry.value.size());
            for (ObjectObjectCursor<String, MappingMetaData> mappingEntry : indexEntry.value) {
                out.writeString(mappingEntry.key);
                mappingEntry.value.writeTo(out);
            }
        }
        out.writeVInt(aliases.size());
        for (ObjectObjectCursor<String, List<AliasMetaData>> indexEntry : aliases) {
            out.writeString(indexEntry.key);
            out.writeVInt(indexEntry.value.size());
            for (AliasMetaData aliasEntry : indexEntry.value) {
                aliasEntry.writeTo(out);
            }
        }
        out.writeVInt(settings.size());
        for (ObjectObjectCursor<String, Settings> indexEntry : settings) {
            out.writeString(indexEntry.key);
            Settings.writeSettingsToStream(indexEntry.value, out);
        }
    }

    public static GetIndexResponse fromXContent(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser::getTokenLocation);
        parser.nextToken();

        List<String> indices = new ArrayList<>();
        ImmutableOpenMap.Builder<String, ImmutableOpenMap<String, MappingMetaData>> mappingsBuilder = ImmutableOpenMap.builder();
        ImmutableOpenMap.Builder<String, Settings> settingsBuilder = ImmutableOpenMap.builder();
        ImmutableOpenMap.Builder<String, List<AliasMetaData>> aliasesBuilder = ImmutableOpenMap.builder();

        ensureExpectedToken(XContentParser.Token.FIELD_NAME, parser.currentToken(), parser::getTokenLocation);
        String indexName = parser.currentName();

        if (indexName == null) {
            return new GetIndexResponse(indices.toArray(new String[indices.size()]), mappingsBuilder.build(),
                aliasesBuilder.build(), settingsBuilder.build());
        }

        for (XContentParser.Token token = parser.nextToken(); token != XContentParser.Token.END_OBJECT; token = parser.nextToken()) {
            if (token == XContentParser.Token.FIELD_NAME) {
                indexName = parser.currentName();
            } else {
                indices.add(indexName);
                String currentFieldName = parser.currentName();
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        currentFieldName = parser.currentName();
                    }
                    if (token == XContentParser.Token.START_OBJECT) {
                        if (SETTINGS.match(currentFieldName, parser.getDeprecationHandler())) {
                            Settings settings = Settings.fromXContent(parser);
                            settingsBuilder.put(indexName, settings);
                        } else if (MAPPINGS.match(currentFieldName, parser.getDeprecationHandler())) {
                            ImmutableOpenMap.Builder<String, MappingMetaData> mappingsWithTypeBuilder = ImmutableOpenMap.builder();
                            while ((parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                                String mappingType = parser.currentName();
                                MappingMetaData mappingMetaData = MappingMetaData.fromXContent(parser);
                                mappingsWithTypeBuilder.put(mappingType, mappingMetaData);
                            }
                            mappingsBuilder.put(indexName, mappingsWithTypeBuilder.build());
                        } else if (ALIASES.match(currentFieldName, parser.getDeprecationHandler())) {
                            List<AliasMetaData> aliasMetaDataList = new ArrayList<>();
                            while ((parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                                AliasMetaData aliasMetaData = AliasMetaData.Builder.fromXContent(parser);
                                aliasMetaDataList.add(aliasMetaData);
                            }
                            aliasesBuilder.put(indexName, aliasMetaDataList);
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
            }
        }

        return new GetIndexResponse(indices.toArray(new String[indices.size()]), mappingsBuilder.build(),
            aliasesBuilder.build(), settingsBuilder.build());
    }
}
