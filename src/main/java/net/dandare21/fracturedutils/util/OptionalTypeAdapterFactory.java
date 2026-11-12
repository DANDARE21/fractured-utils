package net.dandare21.fracturedutils.util;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public class OptionalTypeAdapterFactory implements TypeAdapterFactory {
    public static final OptionalTypeAdapterFactory INSTANCE = new OptionalTypeAdapterFactory();

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != Optional.class) {
            return null;
        }

        Type[] typeArgs = ((ParameterizedType) type.getType()).getActualTypeArguments();
        Type innerType = typeArgs.length > 0 ? typeArgs[0] : Object.class;
        TypeAdapter<?> innerAdapter = gson.getAdapter(TypeToken.get(innerType));
        return (TypeAdapter<T>) new OptionalAdapter<>(innerAdapter);
    }

    private static class OptionalAdapter<E> extends TypeAdapter<Optional<E>> {
        private final TypeAdapter<E> innerAdapter;

        public OptionalAdapter(TypeAdapter<E> innerAdapter) {
            this.innerAdapter = innerAdapter;
        }

        @Override
        public void write(JsonWriter out, Optional<E> value) throws IOException {
            if (value == null || value.isEmpty()) {
                out.nullValue();
            } else {
                innerAdapter.write(out, value.get());
            }
        }

        @Override
        public Optional<E> read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return Optional.empty();
            }
            return Optional.ofNullable(innerAdapter.read(in));
        }
    }
}

