export interface User {
  id: string;
  email: string;
  username: string;
}

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  user: User;
}

