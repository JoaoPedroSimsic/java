export interface Participant {
  id: string;
  name: string;
  avatarUrl?: string;
  online: boolean;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  timestamp: Date;
  status: MessageStatus;
}

export type MessageStatus = 'sending' | 'sent' | 'delivered' | 'read';

export interface Conversation {
  id: string;
  participants: Participant[];
  lastMessage?: Message;
  unreadCount: number;
  updatedAt: Date;
}
