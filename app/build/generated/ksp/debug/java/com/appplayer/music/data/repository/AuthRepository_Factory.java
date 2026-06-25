package com.appplayer.music.data.repository;

import com.appplayer.music.data.api.CantioApiService;
import com.appplayer.music.utils.TokenManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class AuthRepository_Factory implements Factory<AuthRepository> {
  private final Provider<CantioApiService> apiProvider;

  private final Provider<TokenManager> tokenManagerProvider;

  public AuthRepository_Factory(Provider<CantioApiService> apiProvider,
      Provider<TokenManager> tokenManagerProvider) {
    this.apiProvider = apiProvider;
    this.tokenManagerProvider = tokenManagerProvider;
  }

  @Override
  public AuthRepository get() {
    return newInstance(apiProvider.get(), tokenManagerProvider.get());
  }

  public static AuthRepository_Factory create(Provider<CantioApiService> apiProvider,
      Provider<TokenManager> tokenManagerProvider) {
    return new AuthRepository_Factory(apiProvider, tokenManagerProvider);
  }

  public static AuthRepository newInstance(CantioApiService api, TokenManager tokenManager) {
    return new AuthRepository(api, tokenManager);
  }
}
