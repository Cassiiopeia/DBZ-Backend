package com.samcomo.dbz.member.service.impl;

import static com.samcomo.dbz.global.exception.ErrorCode.EMAIL_ALREADY_EXISTS;
import static com.samcomo.dbz.global.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.samcomo.dbz.global.exception.ErrorCode.NICKNAME_ALREADY_EXISTS;
import static com.samcomo.dbz.global.exception.ErrorCode.PROFILE_IMAGE_NOT_UPLOADED;
import static com.samcomo.dbz.member.model.constants.MemberRole.MEMBER;
import static com.samcomo.dbz.member.model.constants.MemberStatus.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

import com.samcomo.dbz.global.s3.constants.ImageUploadState;
import com.samcomo.dbz.global.s3.service.S3Service;
import com.samcomo.dbz.member.exception.MemberException;
import com.samcomo.dbz.member.model.dto.LocationRequest;
import com.samcomo.dbz.member.model.dto.MyPageResponse;
import com.samcomo.dbz.member.model.dto.RegisterRequest;
import com.samcomo.dbz.member.model.entity.Member;
import com.samcomo.dbz.member.model.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

  @InjectMocks
  private MemberServiceImpl memberService;

  @Mock
  private MemberRepository memberRepository;
  @Mock
  private S3Service s3Service;

  @Spy
  private PasswordEncoder passwordEncoder;

  private RegisterRequest request;
  private Member savedMember;
  private String rawEmail;
  private String rawNickname;
  private String rawPhone;
  private String rawPassword;
  private String validAddress;
  private Double validLatitude;
  private Double validLongitude;

  @BeforeEach
  void init() {

    passwordEncoder = new BCryptPasswordEncoder();

    rawEmail = "samcomo@gmail.com";
    rawPhone = "010-1234-5678";
    rawNickname = "삼코모";
    rawPassword = "abcd123!";
    validAddress = "제주시";
    validLatitude = 34.12345;
    validLongitude = 127.12345;

    request = RegisterRequest.builder()
        .email(rawEmail)
        .nickname(rawNickname)
        .phone(rawPhone)
        .password(rawPassword)
        .address(validAddress)
        .latitude(validLatitude)
        .longitude(validLongitude)
        .build();

    savedMember = Member.builder()
        .id(1L)
        .email(rawEmail)
        .nickname(rawNickname)
        .phone(rawPhone)
        .password(passwordEncoder.encode(rawPassword))
        .role(MEMBER)
        .status(ACTIVE)
        .address(validAddress)
        .latitude(validLatitude)
        .longitude(validLongitude)
        .build();
  }

  @Test
  @DisplayName(value = "회원가입[성공]")
  void successRegister() {
    // given
    given(memberRepository.existsByEmail(any())).willReturn(false);
    given(memberRepository.existsByNickname(any())).willReturn(false);

    ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);

    // when
    memberService.register(request);

    // then
    verify(memberRepository, times(1)).existsByEmail(request.getEmail());
    verify(memberRepository, times(1)).existsByNickname(request.getNickname());
    verify(memberRepository, times(1)).save(captor.capture());

    assertEquals(request.getEmail(), captor.getValue().getEmail());
    assertEquals(request.getNickname(), captor.getValue().getNickname());
    assertEquals(request.getPhone(), captor.getValue().getPhone());
    assertEquals(request.getAddress(), captor.getValue().getAddress());
    assertEquals(request.getLatitude(), captor.getValue().getLatitude());
    assertEquals(request.getLongitude(), captor.getValue().getLongitude());

    assertTrue(passwordEncoder.matches(rawPassword, savedMember.getPassword()));
  }

  @Test
  @DisplayName(value = "회원가입[실패] : 이메일 중복")
  void failValidateDuplicateMember_EmailException() {
    // given
    given(memberRepository.existsByEmail(any())).willReturn(true);

    // when
    MemberException memberException = assertThrows(MemberException.class,
        () -> memberService.register(request));

    // then
    assertEquals(EMAIL_ALREADY_EXISTS, memberException.getErrorCode());
  }

  @Test
  @DisplayName(value = "회원가입[실패] : 닉네임 중복")
  void failValidateDuplicateMember_NicknameException() {
    // given
    given(memberRepository.existsByEmail(any())).willReturn(false);
    given(memberRepository.existsByNickname(any())).willReturn(true);

    // when
    MemberException memberException = assertThrows(MemberException.class,
        () -> memberService.register(request));

    // then
    assertEquals(NICKNAME_ALREADY_EXISTS, memberException.getErrorCode());
  }

  @Test
  @DisplayName("마이페이지[성공]")
  void successGetMyInfo() {
    // given
    given(memberRepository.findById(any())).willReturn(Optional.of(savedMember));

    // when
    MyPageResponse myPageResponse = memberService.getMyInfo(savedMember.getId());

    // then
    assertEquals(savedMember.getEmail(), myPageResponse.getEmail());
    assertEquals(savedMember.getNickname(), myPageResponse.getNickname());
    assertEquals(savedMember.getPhone(), myPageResponse.getPhone());
  }

  @Test
  @DisplayName("마이페이지[실패] - DB 조회 실패")
  void failGetMyInfo() {
    // given
    given(memberRepository.findById(any())).willReturn(Optional.empty());

    // when
    MemberException e = assertThrows(MemberException.class,
        () -> memberService.getMyInfo(savedMember.getId()));

    // then
    assertEquals(MEMBER_NOT_FOUND, e.getErrorCode());
  }

  @Test
  @DisplayName("위치업데이트[성공]")
  void successUpdateLocation() {
    // given
    LocationRequest updateRequest = LocationRequest.builder()
        .address("새로운 주소지")
        .longitude(37.12345)
        .latitude(127.12345)
        .build();
    given(memberRepository.findById(any())).willReturn(Optional.of(savedMember));

    Member updatedMember = savedMember;
    updatedMember.setAddress(updateRequest.getAddress());
    updatedMember.setLatitude(updateRequest.getLatitude());
    updatedMember.setLongitude(updateRequest.getLongitude());
    given(memberRepository.save(any())).willReturn(updatedMember);

    ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);

    // when
    memberService.updateLocation(savedMember.getId(), updateRequest);

    // then
    System.out.println(captor.getAllValues());
  }

  @Test
  @DisplayName("위치업데이트[실패] - DB 조회 실패")
  void failUpdateLocation() {
    // given
    LocationRequest updateRequest = LocationRequest.builder()
        .address("새로운 주소")
        .longitude(37.12345)
        .latitude(127.12345)
        .build();
    given(memberRepository.findById(savedMember.getId())).willReturn(Optional.empty());

    // when
    MemberException e = assertThrows(MemberException.class,
        () -> memberService.updateLocation(savedMember.getId(), updateRequest));

    // then
    assertEquals(MEMBER_NOT_FOUND, e.getErrorCode());
  }

  @Test
  @DisplayName("프로필이미지업데이트[성공]")
  void successUpdateProfileImage() {
    // given
    String beforeProfileImageUrl = savedMember.getProfileImageUrl();
    MultipartFile profileImage = new MockMultipartFile(
        "profileImage", "test.png", IMAGE_PNG_VALUE, "test".getBytes());
    ImageUploadState imageUploadState = ImageUploadState.builder()
        .imageUrl("https://samcomo.amazonaws.com/stringimage.jpg")
        .success(true)
        .build();

    // when
    given(memberRepository.findById(anyLong())).willReturn(Optional.of(savedMember));
    given(s3Service.uploadMultipartFileByStream(any(), any())).willReturn(imageUploadState);
    // then

    memberService.updateProfileImage(savedMember.getId(), profileImage);

    assertNull(beforeProfileImageUrl);
    assertEquals("https://samcomo.amazonaws.com/stringimage.jpg", savedMember.getProfileImageUrl());
  }

  @Test
  @DisplayName("프로필이미지업데이트[실패] - s3 업로드 실패")
  void failUpdateProfileImage() {
    // given
    String beforeProfileImageUrl = savedMember.getProfileImageUrl();
    MultipartFile profileImage = new MockMultipartFile(
        "profileImage", "test.png", IMAGE_PNG_VALUE, "test".getBytes());
    ImageUploadState imageUploadState = ImageUploadState.builder()
        .imageUrl(null)
        .success(false)
        .build();

    // when
    given(memberRepository.findById(anyLong())).willReturn(Optional.of(savedMember));
    given(s3Service.uploadMultipartFileByStream(any(), any())).willReturn(imageUploadState);
    // then

    MemberException e = assertThrows(MemberException.class, () -> {
      memberService.updateProfileImage(savedMember.getId(), profileImage);
    });

    assertNull(beforeProfileImageUrl);
    assertNull(savedMember.getProfileImageUrl());
    assertEquals(PROFILE_IMAGE_NOT_UPLOADED, e.getErrorCode());
  }
}