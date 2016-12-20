/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.watcher.execution;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.watcher.trigger.TriggerEvent;
import org.elasticsearch.xpack.watcher.trigger.TriggerService;

import java.io.IOException;

public class TriggeredWatch implements ToXContent {

    private final Wid id;
    private final TriggerEvent triggerEvent;

    public TriggeredWatch(Wid id, TriggerEvent triggerEvent) {
        this.id = id;
        this.triggerEvent = triggerEvent;
    }

    public Wid id() {
        return id;
    }

    public TriggerEvent triggerEvent() {
        return triggerEvent;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startObject(Field.TRIGGER_EVENT.getPreferredName())
                .field(triggerEvent.type(), triggerEvent, params)
                .endObject();
        builder.endObject();
        return builder;
    }

    public static class Parser extends AbstractComponent {

        private final TriggerService triggerService;

        public Parser(Settings settings, TriggerService triggerService) {
            super(settings);
            this.triggerService = triggerService;
        }

        public TriggeredWatch parse(String id, long version, BytesReference source) {
            // EMPTY is safe here because we never use namedObject
            try (XContentParser parser = XContentHelper.createParser(NamedXContentRegistry.EMPTY, source)) {
                return parse(id, version, parser);
            } catch (IOException e) {
                throw new ElasticsearchException("unable to parse watch record", e);
            }
        }

        public TriggeredWatch parse(String id, long version, XContentParser parser) throws IOException {
            assert id != null : "watch record id is missing";

            Wid wid = new Wid(id);
            TriggerEvent triggerEvent = null;

            String currentFieldName = null;
            XContentParser.Token token = parser.nextToken();
            assert token == XContentParser.Token.START_OBJECT;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else if (token == XContentParser.Token.START_OBJECT) {
                    if (ParseFieldMatcher.STRICT.match(currentFieldName, Field.TRIGGER_EVENT)) {
                        triggerEvent = triggerService.parseTriggerEvent(wid.watchId(), id, parser);
                    } else {
                        parser.skipChildren();
                    }
                }
            }

            TriggeredWatch record = new TriggeredWatch(wid, triggerEvent);
            assert record.triggerEvent() != null : "watch record [" + id +"] is missing trigger";
            return record;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TriggeredWatch entry = (TriggeredWatch) o;
        if (!id.equals(entry.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public interface Field {
        ParseField TRIGGER_EVENT = new ParseField("trigger_event");
        ParseField STATE = new ParseField("state");
    }
}
