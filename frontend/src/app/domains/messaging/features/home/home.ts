import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthStore } from '@domains/auth';
import { ConversationStore } from '../../data/conversation.store';
import { Conversation } from '../../models/messaging.models';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="min-h-screen bg-base">
      <header class="sticky top-0 z-10 glass border-b border-surface-emphasis">
        <div class="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between">
          <span class="text-xl font-black tracking-tighter text-main">HERMES</span>

          <div class="flex items-center gap-4">
            <button
              class="relative p-2 rounded-xl hover:bg-surface-hover transition-colors"
              aria-label="Notifications"
            >
              <svg
                class="w-6 h-6 text-muted"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
                />
              </svg>
              @if (conversationStore.totalUnreadCount() > 0) {
                <span
                  class="absolute -top-1 -right-1 w-5 h-5 bg-primary text-inverse text-xs font-bold rounded-full flex items-center justify-center"
                >
                  {{ conversationStore.totalUnreadCount() }}
                </span>
              }
            </button>

            <div class="relative">
              <button
                (click)="toggleUserMenu()"
                class="flex items-center gap-2 p-1.5 rounded-xl hover:bg-surface-hover transition-colors"
              >
                <div
                  class="w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold"
                >
                  {{ userInitial }}
                </div>
              </button>

              @if (showUserMenu) {
                <div
                  class="absolute right-0 mt-2 w-56 glass rounded-xl shadow-2xl border border-surface-emphasis overflow-hidden"
                >
                  <div class="px-4 py-3 border-b border-surface-emphasis">
                    <p class="font-semibold text-main truncate">{{ authStore.user()?.name }}</p>
                    <p class="text-sm text-muted truncate">{{ authStore.user()?.email }}</p>
                  </div>
                  <button
                    (click)="logout()"
                    class="w-full px-4 py-3 text-left text-error hover:bg-surface-hover transition-colors flex items-center gap-2"
                  >
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
                      />
                    </svg>
                    Sign out
                  </button>
                </div>
              }
            </div>
          </div>
        </div>
      </header>

      <main class="max-w-4xl mx-auto px-4 py-6">
        <div class="flex items-center justify-between mb-6">
          <h1 class="text-2xl font-bold text-main">Messages</h1>
          <button
            class="p-3 rounded-xl bg-primary text-inverse shadow-lg shadow-primary/20 hover:opacity-90 transition-opacity"
            aria-label="New conversation"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 4v16m8-8H4"
              />
            </svg>
          </button>
        </div>

        <div class="relative mb-6">
          <svg
            class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          <input
            type="text"
            placeholder="Search conversations..."
            [ngModel]="conversationStore.searchQuery()"
            (ngModelChange)="onSearchChange($event)"
            class="w-full pl-12 pr-4 py-3 rounded-xl bg-surface/50 border border-surface-emphasis text-main placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all"
          />
        </div>

        @if (conversationStore.isLoading()) {
          <div class="flex flex-col gap-4">
            @for (_ of [1, 2, 3]; track $index) {
              <div class="glass-light rounded-2xl p-4 animate-pulse">
                <div class="flex items-center gap-4">
                  <div class="w-14 h-14 rounded-full bg-surface-emphasis"></div>
                  <div class="flex-1">
                    <div class="h-4 bg-surface-emphasis rounded w-1/3 mb-2"></div>
                    <div class="h-3 bg-surface-emphasis rounded w-2/3"></div>
                  </div>
                </div>
              </div>
            }
          </div>
        } @else if (!conversationStore.hasConversations()) {
          <div class="glass-light rounded-3xl p-12 text-center">
            <div
              class="w-20 h-20 mx-auto mb-6 rounded-full bg-primary/10 flex items-center justify-center"
            >
              <svg class="w-10 h-10 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                />
              </svg>
            </div>
            <h2 class="text-xl font-bold text-main mb-2">No conversations yet</h2>
            <p class="text-muted mb-6">Start a new conversation to connect with others</p>
            <button
              class="px-6 py-3 rounded-xl bg-primary text-inverse font-semibold shadow-lg shadow-primary/20 hover:opacity-90 transition-opacity"
            >
              Start a conversation
            </button>
          </div>
        } @else {
          <div class="flex flex-col gap-3">
            @for (conversation of conversationStore.filteredConversations(); track conversation.id) {
              <button
                (click)="openConversation(conversation)"
                class="w-full glass-light rounded-2xl p-4 hover:bg-surface-hover transition-all text-left group"
              >
                <div class="flex items-center gap-4">
                  <div class="relative">
                    <div
                      class="w-14 h-14 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold text-lg"
                    >
                      {{ getParticipantInitial(conversation) }}
                    </div>
                    @if (conversation.participants[0]?.online) {
                      <span
                        class="absolute bottom-0 right-0 w-4 h-4 bg-success rounded-full border-2 border-surface"
                      ></span>
                    }
                  </div>

                  <div class="flex-1 min-w-0">
                    <div class="flex items-center justify-between mb-1">
                      <span class="font-semibold text-main truncate">
                        {{ getParticipantName(conversation) }}
                      </span>
                      <span class="text-xs text-muted shrink-0 ml-2">
                        {{ formatTime(conversation.updatedAt) }}
                      </span>
                    </div>
                    <div class="flex items-center justify-between">
                      <p class="text-sm text-muted truncate pr-2">
                        {{ conversation.lastMessage?.content || 'No messages yet' }}
                      </p>
                      @if (conversation.unreadCount > 0) {
                        <span
                          class="shrink-0 w-6 h-6 bg-primary text-inverse text-xs font-bold rounded-full flex items-center justify-center"
                        >
                          {{ conversation.unreadCount }}
                        </span>
                      }
                    </div>
                  </div>
                </div>
              </button>
            }
          </div>
        }
      </main>
    </div>
  `,
})
export class Home implements OnInit {
  protected readonly authStore = inject(AuthStore);
  protected readonly conversationStore = inject(ConversationStore);

  showUserMenu = false;

  get userInitial(): string {
    const name = this.authStore.user()?.name;
    return name ? name.charAt(0).toUpperCase() : '?';
  }

  ngOnInit() {
    this.conversationStore.loadConversations();
  }

  toggleUserMenu() {
    this.showUserMenu = !this.showUserMenu;
  }

  logout() {
    this.authStore.logout();
  }

  onSearchChange(query: string) {
    this.conversationStore.setSearchQuery(query);
  }

  openConversation(conversation: Conversation) {
    console.log('Opening conversation:', conversation.id);
  }

  getParticipantName(conversation: Conversation): string {
    return conversation.participants[0]?.name || 'Unknown';
  }

  getParticipantInitial(conversation: Conversation): string {
    const name = this.getParticipantName(conversation);
    return name.charAt(0).toUpperCase();
  }

  formatTime(date: Date): string {
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'now';
    if (minutes < 60) return `${minutes}m`;
    if (hours < 24) return `${hours}h`;
    if (days < 7) return `${days}d`;
    return date.toLocaleDateString();
  }
}
