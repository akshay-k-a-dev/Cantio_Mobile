package com.appplayer.music.di;

import com.appplayer.music.data.api.CantioApiService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

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
public final class NetworkModule_ProvideCantioApiServiceFactory implements Factory<CantioApiService> {
  private final Provider<Retrofit> retrofitProvider;

  public NetworkModule_ProvideCantioApiServiceFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public CantioApiService get() {
    return provideCantioApiService(retrofitProvider.get());
  }

  public static NetworkModule_ProvideCantioApiServiceFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new NetworkModule_ProvideCantioApiServiceFactory(retrofitProvider);
  }

  public static CantioApiService provideCantioApiService(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideCantioApiService(retrofit));
  }
}
