import { create } from 'zustand';
import type { MessageDTO, IntentDTO, RecommendationDTO, DebugInfo } from '@/lib/types';
import { createSession, sendMessage, getRecommendationHistory } from '@/lib/api';

const GUEST_ID_KEY = 'shopmind_guest_id';

export type Phase = 'discovery' | 'shortlist' | 'compare' | 'deepdive' | 'archive';
export type NavTab = 'Curations' | 'Intelligence' | 'Archive';
export const PHASE_TO_TAB: Record<Phase, NavTab> = {
  discovery: 'Intelligence',
  shortlist:  'Intelligence',
  compare:    'Intelligence',
  deepdive:   'Archive',
  archive:    'Archive',
};

interface ChatState {
  sessionId: string | null;
  guestId: string | null;
  messages: MessageDTO[];
  intent: IntentDTO | null;
  recommendations: RecommendationDTO[];
  archive: RecommendationDTO[];
  debugInfo: DebugInfo | null;
  isLoading: boolean;
  reasoningStatus: string | null;
  error: string | null;
  backendDown: boolean;
  selectedProduct: RecommendationDTO | null;
  showDebug: boolean;
  phase: Phase;
  compareSet: string[];
  activeProductId: string | null;

  initSession: () => Promise<void>;
  send: (content: string) => Promise<void>;
  loadArchive: () => Promise<void>;
  setSelectedProduct: (product: RecommendationDTO | null) => void;
  toggleDebug: () => void;
  reset: () => void;
  setPhase: (phase: Phase) => void;
  toggleCompare: (id: string) => void;
  setActiveProduct: (id: string) => void;
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessionId: null,
  guestId: null,
  messages: [],
  intent: null,
  recommendations: [],
  archive: [],
  debugInfo: null,
  isLoading: false,
  reasoningStatus: null,
  error: null,
  backendDown: false,
  selectedProduct: null,
  showDebug: false,
  phase: 'discovery',
  compareSet: [],
  activeProductId: null,

  initSession: async () => {
    set({
      sessionId: null, messages: [], intent: null, recommendations: [],
      debugInfo: null, isLoading: false, reasoningStatus: null, error: null,
      backendDown: false, selectedProduct: null, phase: 'discovery',
      compareSet: [], activeProductId: null,
    });
    const storedGuestId = localStorage.getItem(GUEST_ID_KEY) ?? undefined;
    try {
      const { sessionId, guestId } = await createSession(storedGuestId);
      localStorage.setItem(GUEST_ID_KEY, guestId);
      set({ sessionId, guestId, error: null, backendDown: false, phase: 'discovery' });
    } catch {
      set({ backendDown: true, isLoading: false });
    }
  },

  loadArchive: async () => {
    const { guestId } = get();
    if (!guestId) return;
    try {
      const archive = await getRecommendationHistory(guestId);
      set({ archive });
    } catch {
      // silently ignore — archive stays as-is
    }
  },

  send: async (content: string) => {
    const state = get();
    if (!state.sessionId) return;

    const userMessage: MessageDTO = { id: Date.now().toString(), role: 'user', content };
    set({ messages: [...state.messages, userMessage], isLoading: true, reasoningStatus: 'Analyzing your requirements…', error: null });

    try {
      const response = await sendMessage(state.sessionId, content);
      const hasRecs = response.recommendations.length > 0;
      set({
        messages: [...get().messages, response.assistantMessage],
        intent: response.currentIntent || state.intent,
        recommendations: hasRecs ? response.recommendations : state.recommendations,
        debugInfo: response.debugInfo || null,
        isLoading: false,
        reasoningStatus: null,
        phase: hasRecs ? 'shortlist' : 'discovery',
        compareSet: hasRecs ? response.recommendations.map(r => r.id) : state.compareSet,
        activeProductId: hasRecs ? response.recommendations[0]?.id ?? null : state.activeProductId,
      });
    } catch {
      set({ backendDown: true, isLoading: false, reasoningStatus: null });
    }
  },

  setSelectedProduct: (product) => set({
    selectedProduct: product,
    activeProductId: product?.id ?? null,
    phase: product ? 'deepdive' : get().phase,
  }),

  toggleDebug: () => set({ showDebug: !get().showDebug }),

  setPhase: (phase) => set({ phase }),

  toggleCompare: (id) => set(state => {
    const already = state.compareSet.includes(id);
    return { compareSet: already ? state.compareSet.filter(x => x !== id) : [...state.compareSet, id].slice(-3) };
  }),

  setActiveProduct: (id) => set({ activeProductId: id }),

  reset: () => set({
    sessionId: null, messages: [], intent: null, recommendations: [],
    debugInfo: null, isLoading: false, reasoningStatus: null, error: null,
    backendDown: false, selectedProduct: null, showDebug: false, phase: 'discovery',
    compareSet: [], activeProductId: null,
  }),
}));
