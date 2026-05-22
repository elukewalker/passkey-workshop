package com.yubicolabs.passkey_rp.services.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import com.yubicolabs.passkey_rp.interfaces.AttestationRequestStorage;
import com.yubicolabs.passkey_rp.interfaces.CredentialStorage;
import com.yubicolabs.passkey_rp.models.api.AttestationOptionsRequest;
import com.yubicolabs.passkey_rp.models.api.AttestationOptionsResponse;
import com.yubicolabs.passkey_rp.models.api.AttestationResultRequest;
import com.yubicolabs.passkey_rp.models.api.AttestationResultResponse;
import com.yubicolabs.passkey_rp.models.common.AttestationOptions;
import com.yubicolabs.passkey_rp.services.storage.StorageInstance;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("PasskeyOperations")
class PasskeyOperationsTest {

    @Mock
    private RelyingPartyInstance relyingPartyInstance;

    @Mock
    private RelyingParty relyingParty;

    @Mock
    private StorageInstance storageInstance;

    @Mock
    private CredentialStorage credentialStorage;

    @Mock
    private AttestationRequestStorage attestationRequestStorage;

    @Mock
    private com.yubicolabs.passkey_rp.interfaces.AdvancedProtectionStatusStorage advancedProtectionStatusStorage;

    private PasskeyOperations passkeyOperations;

    @BeforeEach
    void setUp() {
        when(relyingPartyInstance.getRelyingParty()).thenReturn(relyingParty);
        when(relyingPartyInstance.getStorageInstance()).thenReturn(storageInstance);
        when(storageInstance.getCredentialStorage()).thenReturn(credentialStorage);
        when(storageInstance.getAttestationRequestStorage()).thenReturn(attestationRequestStorage);
        when(storageInstance.getAdvancedProtectionStatusStorage()).thenReturn(advancedProtectionStatusStorage);

        passkeyOperations = new PasskeyOperations(relyingPartyInstance);
    }

    @Nested
    @DisplayName("Registration Flow")
    class RegistrationFlow {

        @Test
        @DisplayName("should_generate_valid_attestation_options_when_new_user_registers")
        void should_generate_valid_attestation_options_when_new_user_registers() {
            AttestationOptionsRequest request = AttestationOptionsRequest.builder()
                    .userName("test@example.com")
                    .displayName("Test User")
                    .hints(Optional.empty())
                    .build();

            when(credentialStorage.getUserHandleForUsername(anyString())).thenReturn(Optional.empty());

            UserIdentity mockUser = UserIdentity.builder()
                    .name("test@example.com")
                    .displayName("Test User")
                    .id(new ByteArray(new byte[16]))
                    .build();

            PublicKeyCredentialCreationOptions mockPkc = PublicKeyCredentialCreationOptions.builder()
                    .rp(com.yubico.webauthn.data.RelyingPartyIdentity.builder()
                            .id("example.com")
                            .name("Example RP")
                            .build())
                    .user(mockUser)
                    .challenge(new ByteArray(new byte[32]))
                    .pubKeyCredParams(java.util.Collections.singletonList(
                            com.yubico.webauthn.data.PublicKeyCredentialParameters.ES256))
                    .build();

            when(relyingParty.startRegistration(any())).thenReturn(mockPkc);

            AttestationOptionsResponse response = passkeyOperations.attestationOptions(request);

            assertThat(response).isNotNull();
            assertThat(response.getRequestId()).isNotNull();
            assertThat(response.getPublicKey()).isEqualTo(mockPkc);
            verify(attestationRequestStorage).insert(any(), anyString());
        }

        @Test
        @DisplayName("should_persist_credential_when_attestation_result_is_valid")
        void should_persist_credential_when_attestation_result_is_valid() throws Exception {
            String requestId = "test-request-id";
            ByteArray userHandle = new ByteArray(new byte[16]);
            ByteArray credentialId = new ByteArray(new byte[32]);

            UserIdentity mockUser = UserIdentity.builder()
                    .name("test@example.com")
                    .displayName("Test User")
                    .id(userHandle)
                    .build();

            PublicKeyCredentialCreationOptions mockRequest = PublicKeyCredentialCreationOptions.builder()
                    .rp(com.yubico.webauthn.data.RelyingPartyIdentity.builder()
                            .id("example.com")
                            .name("Example RP")
                            .build())
                    .user(mockUser)
                    .challenge(new ByteArray(new byte[32]))
                    .pubKeyCredParams(java.util.Collections.singletonList(
                            com.yubico.webauthn.data.PublicKeyCredentialParameters.ES256))
                    .build();

            AttestationOptions mockOptions = AttestationOptions.builder()
                    .attestationRequest(mockRequest)
                    .isActive(true)
                    .build();

            when(attestationRequestStorage.getIfPresent(requestId))
                    .thenReturn(Optional.of(mockOptions));
            when(advancedProtectionStatusStorage.getIfPresent(anyString())).thenReturn(Optional.empty());

            RegistrationResult mockRegResult = org.mockito.Mockito.mock(RegistrationResult.class);
            when(mockRegResult.getKeyId()).thenReturn(
                    com.yubico.webauthn.data.PublicKeyCredentialDescriptor.builder()
                            .id(credentialId)
                            .build());
            when(mockRegResult.getPublicKeyCose()).thenReturn(new ByteArray(new byte[77]));
            when(mockRegResult.getSignatureCount()).thenReturn(0L);
            when(mockRegResult.isAttestationTrusted()).thenReturn(false);

            when(relyingParty.finishRegistration(any())).thenReturn(mockRegResult);
            when(credentialStorage.addRegistration(any())).thenReturn(true);

            @SuppressWarnings("unchecked")
            com.yubico.webauthn.data.PublicKeyCredential<
                    com.yubico.webauthn.data.AuthenticatorAttestationResponse,
                    com.yubico.webauthn.data.ClientRegistrationExtensionOutputs> mockCredential =
                    org.mockito.Mockito.mock(com.yubico.webauthn.data.PublicKeyCredential.class);

            AttestationResultRequest request = new AttestationResultRequest();
            request.setRequestId(requestId);
            request.setMakeCredentialResult(mockCredential);

            AttestationResultResponse response = passkeyOperations.attestationResult(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("created");
            assertThat(response.getCredential()).isNotNull();
            assertThat(response.getCredential().getId()).isEqualTo(credentialId.getBase64Url());
            verify(attestationRequestStorage).invalidate(requestId);
            verify(credentialStorage).addRegistration(any());
        }
    }

    @Nested
    @DisplayName("Assertion Flow")
    class AssertionFlow {

        @Mock
        private com.yubicolabs.passkey_rp.interfaces.AssertionRequestStorage assertionRequestStorage;

        @BeforeEach
        void setUpAssertion() {
            when(storageInstance.getAssertionRequestStorage()).thenReturn(assertionRequestStorage);
        }

        @Test
        @DisplayName("should_generate_valid_assertion_options_when_user_authenticates")
        void should_generate_valid_assertion_options_when_user_authenticates() {
            com.yubicolabs.passkey_rp.models.api.AssertionOptionsRequest request =
                    com.yubicolabs.passkey_rp.models.api.AssertionOptionsRequest.builder()
                            .userName("test@example.com")
                            .hints(Optional.empty())
                            .build();

            com.yubico.webauthn.data.PublicKeyCredentialRequestOptions mockPkro =
                    com.yubico.webauthn.data.PublicKeyCredentialRequestOptions.builder()
                            .challenge(new ByteArray(new byte[32]))
                            .build();

            com.yubico.webauthn.AssertionRequest mockAssertionRequest =
                    org.mockito.Mockito.mock(com.yubico.webauthn.AssertionRequest.class);
            when(mockAssertionRequest.getPublicKeyCredentialRequestOptions()).thenReturn(mockPkro);

            when(relyingParty.startAssertion(any())).thenReturn(mockAssertionRequest);

            com.yubicolabs.passkey_rp.models.api.AssertionOptionsResponse response =
                    passkeyOperations.assertionOptions(request);

            assertThat(response).isNotNull();
            assertThat(response.getRequestId()).isNotNull();
            assertThat(response.getPublicKey()).isEqualTo(mockPkro);
            verify(assertionRequestStorage).insert(any(), anyString());
        }

        @Test
        @DisplayName("should_validate_assertion_and_return_loa_when_assertion_result_is_valid")
        void should_validate_assertion_and_return_loa_when_assertion_result_is_valid() throws Exception {
            String requestId = "test-request-id";
            ByteArray credentialId = new ByteArray(new byte[32]);
            ByteArray userHandle = new ByteArray(new byte[16]);

            com.yubico.webauthn.data.PublicKeyCredentialRequestOptions mockPkro =
                    com.yubico.webauthn.data.PublicKeyCredentialRequestOptions.builder()
                            .challenge(new ByteArray(new byte[32]))
                            .build();

            com.yubico.webauthn.AssertionRequest mockAssertionRequest =
                    com.yubico.webauthn.AssertionRequest.builder()
                            .publicKeyCredentialRequestOptions(mockPkro)
                            .username(Optional.of("test@example.com"))
                            .build();

            com.yubicolabs.passkey_rp.models.common.AssertionOptions mockOptions =
                    com.yubicolabs.passkey_rp.models.common.AssertionOptions.builder()
                            .assertionRequest(mockAssertionRequest)
                            .isActive(true)
                            .build();

            when(assertionRequestStorage.getIfPresent(requestId))
                    .thenReturn(Optional.of(mockOptions));

            com.yubico.webauthn.AssertionResult mockAssertionResult =
                    org.mockito.Mockito.mock(com.yubico.webauthn.AssertionResult.class);
            when(mockAssertionResult.isSuccess()).thenReturn(true);
            when(mockAssertionResult.getCredential()).thenReturn(
                    com.yubico.webauthn.RegisteredCredential.builder()
                            .credentialId(credentialId)
                            .userHandle(userHandle)
                            .publicKeyCose(new ByteArray(new byte[77]))
                            .build());

            when(relyingParty.finishAssertion(any())).thenReturn(mockAssertionResult);

            com.yubicolabs.passkey_rp.models.common.CredentialRegistration mockCredential =
                    com.yubicolabs.passkey_rp.models.common.CredentialRegistration.builder()
                            .credential(com.yubico.webauthn.RegisteredCredential.builder()
                                    .credentialId(credentialId)
                                    .userHandle(userHandle)
                                    .publicKeyCose(new ByteArray(new byte[77]))
                                    .build())
                            .isHighAssurance(true)
                            .build();

            when(credentialStorage.getByCredentialId(credentialId))
                    .thenReturn(java.util.Collections.singleton(mockCredential));

            @SuppressWarnings("unchecked")
            com.yubico.webauthn.data.PublicKeyCredential<
                    com.yubico.webauthn.data.AuthenticatorAssertionResponse,
                    com.yubico.webauthn.data.ClientAssertionExtensionOutputs> mockAssertionCredential =
                    org.mockito.Mockito.mock(com.yubico.webauthn.data.PublicKeyCredential.class);

            com.yubicolabs.passkey_rp.models.api.AssertionResultRequest request =
                    new com.yubicolabs.passkey_rp.models.api.AssertionResultRequest();
            request.setRequestId(requestId);
            request.setAssertionResult(mockAssertionCredential);

            com.yubicolabs.passkey_rp.models.api.AssertionResultResponse response =
                    passkeyOperations.assertionResponse(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("ok");
            assertThat(response.getLoa()).isEqualTo(
                    com.yubicolabs.passkey_rp.models.api.AssertionResultResponse.loaEnum.HIGH);
            verify(assertionRequestStorage).invalidate(requestId);
            verify(relyingParty).finishAssertion(any());
        }
    }
}
