import axios from 'axios';
import type { ConversationResponse, SessionResponse } from './types';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

export async function createSession(): Promise<string> {
  const { data } = await api.post<{ sessionId: string }>('/conversations');
  return data.sessionId;
}

export async function sendMessage(sessionId: string, content: string): Promise<ConversationResponse> {
  const { data } = await api.post<ConversationResponse>(
    `/conversations/${sessionId}/messages`,
    { content }
  );
  return data;
}

export async function getSession(sessionId: string): Promise<SessionResponse> {
  const { data } = await api.get<SessionResponse>(`/conversations/${sessionId}`);
  return data;
}

export default api;
