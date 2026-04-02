import { Injectable, signal, computed } from '@angular/core';
import { Conversation } from '../models/messaging.models';

@Injectable({ providedIn: 'root' })
export class ConversationStore {
  private readonly _conversations = signal<Conversation[]>([]);
  private readonly _loading = signal(false);
  private readonly _searchQuery = signal('');

  readonly conversations = this._conversations.asReadonly();
  readonly isLoading = this._loading.asReadonly();
  readonly searchQuery = this._searchQuery.asReadonly();

  readonly filteredConversations = computed(() => {
    const query = this._searchQuery().toLowerCase().trim();
    if (!query) return this._conversations();

    return this._conversations().filter((conversation) =>
      conversation.participants.some((p) => p.name.toLowerCase().includes(query)),
    );
  });

  readonly hasConversations = computed(() => this._conversations().length > 0);

  readonly totalUnreadCount = computed(() =>
    this._conversations().reduce((sum, c) => sum + c.unreadCount, 0),
  );

  setSearchQuery(query: string) {
    this._searchQuery.set(query);
  }

  loadConversations() {
    this._loading.set(true);
    setTimeout(() => {
      this._conversations.set(this.getMockConversations());
      this._loading.set(false);
    }, 500);
  }

  private getMockConversations(): Conversation[] {
    return [
      {
        id: '1',
        participants: [{ id: 'u1', name: 'Alice Johnson', online: true }],
        lastMessage: {
          id: 'm1',
          conversationId: '1',
          senderId: 'u1',
          content: 'Hey! Are you coming to the meeting?',
          timestamp: new Date(Date.now() - 1000 * 60 * 5),
          status: 'read',
        },
        unreadCount: 2,
        updatedAt: new Date(Date.now() - 1000 * 60 * 5),
      },
      {
        id: '2',
        participants: [{ id: 'u2', name: 'Bob Smith', online: false }],
        lastMessage: {
          id: 'm2',
          conversationId: '2',
          senderId: 'u2',
          content: 'The project looks great!',
          timestamp: new Date(Date.now() - 1000 * 60 * 30),
          status: 'delivered',
        },
        unreadCount: 0,
        updatedAt: new Date(Date.now() - 1000 * 60 * 30),
      },
      {
        id: '3',
        participants: [{ id: 'u3', name: 'Carol Davis', online: true }],
        lastMessage: {
          id: 'm3',
          conversationId: '3',
          senderId: 'u3',
          content: 'Can we schedule a call tomorrow?',
          timestamp: new Date(Date.now() - 1000 * 60 * 60 * 2),
          status: 'read',
        },
        unreadCount: 1,
        updatedAt: new Date(Date.now() - 1000 * 60 * 60 * 2),
      },
    ];
  }
}
