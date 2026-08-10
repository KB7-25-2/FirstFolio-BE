package org.firstfolio.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.auth.dto.request.SignupRequest;
import org.firstfolio.auth.dto.response.LoginResponse;
import org.firstfolio.auth.dto.response.SignupResponse;
import org.firstfolio.auth.service.AuthUseCase;
import org.firstfolio.auth.service.LoginResult;
import org.firstfolio.auth.service.SignupResult;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "인증", description = "Firebase ID Token 기반 회원가입·로그인·로그아웃 API")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/signup")
    @Operation(
            summary = "회원가입",
            description = "Firebase ID Token과 필수 약관 동의, 가입 정보를 검증해 FirstFolio 계정을 생성합니다. "
                    + "Firebase UID와 이메일은 검증된 토큰에서 추출하며 토큰 원문은 저장하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "회원가입 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.Signup.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_SIGNUP_INPUT - 가입 정보 또는 필수 약관 동의가 올바르지 않음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "INVALID_ID_TOKEN - Firebase ID Token이 없거나 유효하지 않음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "ACCOUNT_CONFLICT - 이메일·인증 계정 또는 닉네임 중복"
                    )
            }
    )
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Parameter(hidden = true)
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "공개 닉네임과 필수 약관 동의 여부"
            )
            @RequestBody(required = false) SignupRequest request
    ) {
        SignupResult result = authUseCase.signup(authorizationHeader, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(SignupResponse.from(result)));
    }

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "Firebase ID Token을 검증하고 FirstFolio 회원의 로그인 가능 상태와 다음 온보딩 진입 단계를 확인합니다. "
                    + "성공하면 최종 로그인 시각을 갱신합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "로그인 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.Login.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "INVALID_ID_TOKEN - Firebase ID Token이 없거나 유효하지 않음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "ACCOUNT_NOT_ACTIVE - 이용할 수 없는 계정 상태"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "SIGNUP_REQUIRED - Firebase 인증 계정에 연결된 FirstFolio 회원이 없음"
                    )
            }
    )
    public ApiResponse<LoginResponse> login(
            @Parameter(hidden = true)
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader
    ) {
        LoginResult result = authUseCase.login(authorizationHeader);
        return ApiResponse.of(LoginResponse.from(result));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "로그아웃",
            description = "Firebase 사용자의 로그아웃 요청을 검증합니다. 성공 후 클라이언트가 Firebase Client SDK의 signOut을 호출해야 합니다. "
                    + "현재 기기 로그아웃만 지원하며 재호출해도 같은 결과를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "204", description = "로그아웃 성공, 응답 본문 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "UNAUTHORIZED - 인증 토큰이 없거나 유효하지 않음"
                    )
            }
    )
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true)
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader
    ) {
        authUseCase.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }
}
