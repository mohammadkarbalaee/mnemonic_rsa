// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: release.proto

package de.deutschepost.pi.mlservices.protobuf;

public interface FvLcSetCandidateCommandMessageOrBuilder extends
    // @@protoc_insertion_point(interface_extends:websocket.FvLcSetCandidateCommandMessage)
    com.google.protobuf.MessageLiteOrBuilder {

  /**
   * <pre>
   * The raw bytes of a supported image type (jpg or png), must be smaller than the
   * overall image size limit
   * </pre>
   *
   * <code>bytes image = 1;</code>
   * @return The image.
   */
  com.google.protobuf.ByteString getImage();
}
