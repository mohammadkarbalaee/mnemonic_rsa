// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: release.proto

package de.deutschepost.pi.mlservices.protobuf;

/**
 * <pre>
 * A response from the server, sent after the reference image was set and after every frame grab sent
 * until the process is deemed finished by the server, in which case a FvLcCheckResult response is
 * sent.
 * </pre>
 *
 * Protobuf type {@code websocket.FvLcChallengeResponse}
 */
public  final class FvLcChallengeResponse extends
    com.google.protobuf.GeneratedMessageLite<
        FvLcChallengeResponse, FvLcChallengeResponse.Builder> implements
    // @@protoc_insertion_point(message_implements:websocket.FvLcChallengeResponse)
    FvLcChallengeResponseOrBuilder {
  private FvLcChallengeResponse() {
  }
  /**
   * <pre>
   * Challenge for the user in the vertical (↑↓) direction
   * </pre>
   *
   * Protobuf enum {@code websocket.FvLcChallengeResponse.TiltChallenge}
   */
  public enum TiltChallenge
      implements com.google.protobuf.Internal.EnumLite {
    /**
     * <pre>
     * challenges the user to look up
     * </pre>
     *
     * <code>UP = 0;</code>
     */
    UP(0),
    /**
     * <pre>
     * challenges the user to center their head vertically
     * </pre>
     *
     * <code>CENTER_TILT = 1;</code>
     */
    CENTER_TILT(1),
    /**
     * <pre>
     * challenges the user to look down
     * </pre>
     *
     * <code>DOWN = 2;</code>
     */
    DOWN(2),
    UNRECOGNIZED(-1),
    ;

    /**
     * <pre>
     * challenges the user to look up
     * </pre>
     *
     * <code>UP = 0;</code>
     */
    public static final int UP_VALUE = 0;
    /**
     * <pre>
     * challenges the user to center their head vertically
     * </pre>
     *
     * <code>CENTER_TILT = 1;</code>
     */
    public static final int CENTER_TILT_VALUE = 1;
    /**
     * <pre>
     * challenges the user to look down
     * </pre>
     *
     * <code>DOWN = 2;</code>
     */
    public static final int DOWN_VALUE = 2;


    @Override
    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException(
            "Can't get the number of an unknown enum value.");
      }
      return value;
    }

    /**
     * @param value The number of the enum to look for.
     * @return The enum associated with the given number.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @Deprecated
    public static TiltChallenge valueOf(int value) {
      return forNumber(value);
    }

    public static TiltChallenge forNumber(int value) {
      switch (value) {
        case 0: return UP;
        case 1: return CENTER_TILT;
        case 2: return DOWN;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<TiltChallenge>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        TiltChallenge> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<TiltChallenge>() {
            @Override
            public TiltChallenge findValueByNumber(int number) {
              return TiltChallenge.forNumber(number);
            }
          };

    public static com.google.protobuf.Internal.EnumVerifier 
        internalGetVerifier() {
      return TiltChallengeVerifier.INSTANCE;
    }

    private static final class TiltChallengeVerifier implements 
         com.google.protobuf.Internal.EnumVerifier { 
            static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new TiltChallengeVerifier();
            @Override
            public boolean isInRange(int number) {
              return TiltChallenge.forNumber(number) != null;
            }
          };

    private final int value;

    private TiltChallenge(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:websocket.FvLcChallengeResponse.TiltChallenge)
  }

  /**
   * <pre>
   * Challenge for the user in the horizontal (←→) direction
   * </pre>
   *
   * Protobuf enum {@code websocket.FvLcChallengeResponse.PanChallenge}
   */
  public enum PanChallenge
      implements com.google.protobuf.Internal.EnumLite {
    /**
     * <pre>
     * Challenges the user to look to their left
     * </pre>
     *
     * <code>LEFT = 0;</code>
     */
    LEFT(0),
    /**
     * <pre>
     * Challenges the user to center their head horizontally
     * </pre>
     *
     * <code>CENTER_PAN = 1;</code>
     */
    CENTER_PAN(1),
    /**
     * <pre>
     * Challenges the user to look to their right
     * </pre>
     *
     * <code>RIGHT = 2;</code>
     */
    RIGHT(2),
    UNRECOGNIZED(-1),
    ;

    /**
     * <pre>
     * Challenges the user to look to their left
     * </pre>
     *
     * <code>LEFT = 0;</code>
     */
    public static final int LEFT_VALUE = 0;
    /**
     * <pre>
     * Challenges the user to center their head horizontally
     * </pre>
     *
     * <code>CENTER_PAN = 1;</code>
     */
    public static final int CENTER_PAN_VALUE = 1;
    /**
     * <pre>
     * Challenges the user to look to their right
     * </pre>
     *
     * <code>RIGHT = 2;</code>
     */
    public static final int RIGHT_VALUE = 2;


    @Override
    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException(
            "Can't get the number of an unknown enum value.");
      }
      return value;
    }

    /**
     * @param value The number of the enum to look for.
     * @return The enum associated with the given number.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @Deprecated
    public static PanChallenge valueOf(int value) {
      return forNumber(value);
    }

    public static PanChallenge forNumber(int value) {
      switch (value) {
        case 0: return LEFT;
        case 1: return CENTER_PAN;
        case 2: return RIGHT;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<PanChallenge>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static final com.google.protobuf.Internal.EnumLiteMap<
        PanChallenge> internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<PanChallenge>() {
            @Override
            public PanChallenge findValueByNumber(int number) {
              return PanChallenge.forNumber(number);
            }
          };

    public static com.google.protobuf.Internal.EnumVerifier 
        internalGetVerifier() {
      return PanChallengeVerifier.INSTANCE;
    }

    private static final class PanChallengeVerifier implements 
         com.google.protobuf.Internal.EnumVerifier { 
            static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new PanChallengeVerifier();
            @Override
            public boolean isInRange(int number) {
              return PanChallenge.forNumber(number) != null;
            }
          };

    private final int value;

    private PanChallenge(int value) {
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:websocket.FvLcChallengeResponse.PanChallenge)
  }

  public static final int TILT_CHALLENGE_FIELD_NUMBER = 1;
  private int tiltChallenge_;
  /**
   * <pre>
   * Challenge for the user in the vertical (↑↓) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
   * @return The enum numeric value on the wire for tiltChallenge.
   */
  @Override
  public int getTiltChallengeValue() {
    return tiltChallenge_;
  }
  /**
   * <pre>
   * Challenge for the user in the vertical (↑↓) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
   * @return The tiltChallenge.
   */
  @Override
  public TiltChallenge getTiltChallenge() {
    TiltChallenge result = TiltChallenge.forNumber(tiltChallenge_);
    return result == null ? TiltChallenge.UNRECOGNIZED : result;
  }
  /**
   * <pre>
   * Challenge for the user in the vertical (↑↓) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
   * @param value The enum numeric value on the wire for tiltChallenge to set.
   */
  private void setTiltChallengeValue(int value) {
      tiltChallenge_ = value;
  }
  /**
   * <pre>
   * Challenge for the user in the vertical (↑↓) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
   * @param value The tiltChallenge to set.
   */
  private void setTiltChallenge(TiltChallenge value) {
    tiltChallenge_ = value.getNumber();
    
  }
  /**
   * <pre>
   * Challenge for the user in the vertical (↑↓) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
   */
  private void clearTiltChallenge() {
    
    tiltChallenge_ = 0;
  }

  public static final int PAN_CHALLENGE_FIELD_NUMBER = 2;
  private int panChallenge_;
  /**
   * <pre>
   * Challenge for the user in the horizontal (←→) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
   * @return The enum numeric value on the wire for panChallenge.
   */
  @Override
  public int getPanChallengeValue() {
    return panChallenge_;
  }
  /**
   * <pre>
   * Challenge for the user in the horizontal (←→) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
   * @return The panChallenge.
   */
  @Override
  public PanChallenge getPanChallenge() {
    PanChallenge result = PanChallenge.forNumber(panChallenge_);
    return result == null ? PanChallenge.UNRECOGNIZED : result;
  }
  /**
   * <pre>
   * Challenge for the user in the horizontal (←→) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
   * @param value The enum numeric value on the wire for panChallenge to set.
   */
  private void setPanChallengeValue(int value) {
      panChallenge_ = value;
  }
  /**
   * <pre>
   * Challenge for the user in the horizontal (←→) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
   * @param value The panChallenge to set.
   */
  private void setPanChallenge(PanChallenge value) {
    panChallenge_ = value.getNumber();
    
  }
  /**
   * <pre>
   * Challenge for the user in the horizontal (←→) direction
   * </pre>
   *
   * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
   */
  private void clearPanChallenge() {
    
    panChallenge_ = 0;
  }

  public static FvLcChallengeResponse parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static FvLcChallengeResponse parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static FvLcChallengeResponse parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static FvLcChallengeResponse parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static FvLcChallengeResponse parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data);
  }
  public static FvLcChallengeResponse parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, data, extensionRegistry);
  }
  public static FvLcChallengeResponse parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static FvLcChallengeResponse parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static FvLcChallengeResponse parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input);
  }
  public static FvLcChallengeResponse parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
  }
  public static FvLcChallengeResponse parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input);
  }
  public static FvLcChallengeResponse parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageLite.parseFrom(
        DEFAULT_INSTANCE, input, extensionRegistry);
  }

  public static Builder newBuilder() {
    return (Builder) DEFAULT_INSTANCE.createBuilder();
  }
  public static Builder newBuilder(FvLcChallengeResponse prototype) {
    return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
  }

  /**
   * <pre>
   * A response from the server, sent after the reference image was set and after every frame grab sent
   * until the process is deemed finished by the server, in which case a FvLcCheckResult response is
   * sent.
   * </pre>
   *
   * Protobuf type {@code websocket.FvLcChallengeResponse}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageLite.Builder<
        FvLcChallengeResponse, Builder> implements
      // @@protoc_insertion_point(builder_implements:websocket.FvLcChallengeResponse)
      FvLcChallengeResponseOrBuilder {
    // Construct using de.deutschepost.pi.mlservices.protobuf.FvLcChallengeResponse.newBuilder()
    private Builder() {
      super(DEFAULT_INSTANCE);
    }


    /**
     * <pre>
     * Challenge for the user in the vertical (↑↓) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
     * @return The enum numeric value on the wire for tiltChallenge.
     */
    @Override
    public int getTiltChallengeValue() {
      return instance.getTiltChallengeValue();
    }
    /**
     * <pre>
     * Challenge for the user in the vertical (↑↓) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
     * @param value The tiltChallenge to set.
     * @return This builder for chaining.
     */
    public Builder setTiltChallengeValue(int value) {
      copyOnWrite();
      instance.setTiltChallengeValue(value);
      return this;
    }
    /**
     * <pre>
     * Challenge for the user in the vertical (↑↓) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
     * @return The tiltChallenge.
     */
    @Override
    public TiltChallenge getTiltChallenge() {
      return instance.getTiltChallenge();
    }
    /**
     * <pre>
     * Challenge for the user in the vertical (↑↓) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
     * @param value The enum numeric value on the wire for tiltChallenge to set.
     * @return This builder for chaining.
     */
    public Builder setTiltChallenge(TiltChallenge value) {
      copyOnWrite();
      instance.setTiltChallenge(value);
      return this;
    }
    /**
     * <pre>
     * Challenge for the user in the vertical (↑↓) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.TiltChallenge tilt_challenge = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearTiltChallenge() {
      copyOnWrite();
      instance.clearTiltChallenge();
      return this;
    }

    /**
     * <pre>
     * Challenge for the user in the horizontal (←→) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
     * @return The enum numeric value on the wire for panChallenge.
     */
    @Override
    public int getPanChallengeValue() {
      return instance.getPanChallengeValue();
    }
    /**
     * <pre>
     * Challenge for the user in the horizontal (←→) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
     * @param value The panChallenge to set.
     * @return This builder for chaining.
     */
    public Builder setPanChallengeValue(int value) {
      copyOnWrite();
      instance.setPanChallengeValue(value);
      return this;
    }
    /**
     * <pre>
     * Challenge for the user in the horizontal (←→) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
     * @return The panChallenge.
     */
    @Override
    public PanChallenge getPanChallenge() {
      return instance.getPanChallenge();
    }
    /**
     * <pre>
     * Challenge for the user in the horizontal (←→) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
     * @param value The enum numeric value on the wire for panChallenge to set.
     * @return This builder for chaining.
     */
    public Builder setPanChallenge(PanChallenge value) {
      copyOnWrite();
      instance.setPanChallenge(value);
      return this;
    }
    /**
     * <pre>
     * Challenge for the user in the horizontal (←→) direction
     * </pre>
     *
     * <code>.websocket.FvLcChallengeResponse.PanChallenge pan_challenge = 2;</code>
     * @return This builder for chaining.
     */
    public Builder clearPanChallenge() {
      copyOnWrite();
      instance.clearPanChallenge();
      return this;
    }

    // @@protoc_insertion_point(builder_scope:websocket.FvLcChallengeResponse)
  }
  @Override
  @SuppressWarnings({"unchecked", "fallthrough"})
  protected final Object dynamicMethod(
      MethodToInvoke method,
      Object arg0, Object arg1) {
    switch (method) {
      case NEW_MUTABLE_INSTANCE: {
        return new FvLcChallengeResponse();
      }
      case NEW_BUILDER: {
        return new Builder();
      }
      case BUILD_MESSAGE_INFO: {
          Object[] objects = new Object[] {
            "tiltChallenge_",
            "panChallenge_",
          };
          String info =
              "\u0000\u0002\u0000\u0000\u0001\u0002\u0002\u0000\u0000\u0000\u0001\f\u0002\f";
          return newMessageInfo(DEFAULT_INSTANCE, info, objects);
      }
      // fall through
      case GET_DEFAULT_INSTANCE: {
        return DEFAULT_INSTANCE;
      }
      case GET_PARSER: {
        com.google.protobuf.Parser<FvLcChallengeResponse> parser = PARSER;
        if (parser == null) {
          synchronized (FvLcChallengeResponse.class) {
            parser = PARSER;
            if (parser == null) {
              parser =
                  new DefaultInstanceBasedParser<FvLcChallengeResponse>(
                      DEFAULT_INSTANCE);
              PARSER = parser;
            }
          }
        }
        return parser;
    }
    case GET_MEMOIZED_IS_INITIALIZED: {
      return (byte) 1;
    }
    case SET_MEMOIZED_IS_INITIALIZED: {
      return null;
    }
    }
    throw new UnsupportedOperationException();
  }


  // @@protoc_insertion_point(class_scope:websocket.FvLcChallengeResponse)
  private static final FvLcChallengeResponse DEFAULT_INSTANCE;
  static {
    FvLcChallengeResponse defaultInstance = new FvLcChallengeResponse();
    // New instances are implicitly immutable so no need to make
    // immutable.
    DEFAULT_INSTANCE = defaultInstance;
    com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
      FvLcChallengeResponse.class, defaultInstance);
  }

  public static FvLcChallengeResponse getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static volatile com.google.protobuf.Parser<FvLcChallengeResponse> PARSER;

  public static com.google.protobuf.Parser<FvLcChallengeResponse> parser() {
    return DEFAULT_INSTANCE.getParserForType();
  }
}

