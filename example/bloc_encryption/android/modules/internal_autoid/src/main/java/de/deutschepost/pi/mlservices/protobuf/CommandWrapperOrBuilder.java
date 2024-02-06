// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: command_short.proto

package de.deutschepost.pi.mlservices.protobuf;

public interface CommandWrapperOrBuilder extends
    // @@protoc_insertion_point(interface_extends:websocket.CommandWrapper)
    com.google.protobuf.MessageLiteOrBuilder {

  /**
   * <pre>
   * should currently be "2", reserved for future use
   * </pre>
   *
   * <code>sfixed32 major_version = 1;</code>
   * @return The majorVersion.
   */
  int getMajorVersion();

  /**
   * <pre>
   * should be "0", reserved for future use
   * </pre>
   *
   * <code>sfixed32 minor_version = 2;</code>
   * @return The minorVersion.
   */
  int getMinorVersion();

  /**
   * <pre>
   * the actual command, resolved via reflection on the server side
   * </pre>
   *
   * <code>.google.protobuf.Any command = 3;</code>
   * @return Whether the command field is set.
   */
  boolean hasCommand();
  /**
   * <pre>
   * the actual command, resolved via reflection on the server side
   * </pre>
   *
   * <code>.google.protobuf.Any command = 3;</code>
   * @return The command.
   */
  com.google.protobuf.Any getCommand();
}
