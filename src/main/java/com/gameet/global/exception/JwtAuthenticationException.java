package com.gameet.global.exception;

public class JwtAuthenticationException extends CustomException {

  public JwtAuthenticationException(ErrorCode errorCode) {
      super(errorCode);
  }
}
