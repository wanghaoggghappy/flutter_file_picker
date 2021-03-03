package com.mr.flutter.plugin.filepicker;

import java.util.HashMap;

public class FileInfo {

    final String path;
    final String uri;
    final String name;
    final int size;
    final byte[] bytes;

    public FileInfo(String uri, String path, String name, int size, byte[] bytes) {
        this.uri = uri;
        this.path = path;
        this.name = name;
        this.size = size;
        this.bytes = bytes;
    }

    public static class Builder {

        private String path;
        private String uri;
        private String name;
        private int size;
        private byte[] bytes;

        public Builder withPath(String path){
            this.path = path;
            return this;
        }

        public Builder withName(String name){
            this.name = name;
            return this;
        }

        public Builder withSize(int size){
            this.size = size;
            return this;
        }

        public Builder withData(byte[] bytes){
            this.bytes = bytes;
            return this;
        }

        public Builder withUri(String uri){
            this.uri = uri;
            return this;
        }

        public FileInfo build() {
            return new FileInfo(this.uri, this.path, this.name, this.size, this.bytes);
        }
    }


    public HashMap<String, Object> toMap() {
        final HashMap<String, Object> data = new HashMap<>();
        data.put("path", path);
        data.put("uri", uri);
        data.put("name", name);
        data.put("size", size);
        data.put("bytes", bytes);
        return data;
    }
}
