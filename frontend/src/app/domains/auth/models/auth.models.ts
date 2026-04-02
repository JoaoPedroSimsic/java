export interface User {
  id: string;
  email: string;
  name: string;
  emailVerified: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  message: string;
  user: User;
  accessToken?: string;
}

export interface GitHubAuthResponse {
  authUrl: string;
  state: string;
}

