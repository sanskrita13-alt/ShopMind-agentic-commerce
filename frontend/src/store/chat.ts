import { create } from 'zustand';
import type { MessageDTO, IntentDTO, RecommendationDTO, DebugInfo } from '@/lib/types';
import { createSession, sendMessage } from '@/lib/api';

export type Phase = 'discovery' | 'shortlist' | 'compare' | 'deepdive';
export type NavTab = 'Curations' | 'Intelligence' | 'Archive';
export const PHASE_TO_TAB: Record<Phase, NavTab> = {
  discovery: 'Intelligence',
  shortlist:  'Intelligence',
  compare:    'Intelligence',
  deepdive:   'Archive',
};

interface ChatState {
  sessionId: string | null;
  messages: MessageDTO[];
  intent: IntentDTO | null;
  recommendations: RecommendationDTO[];
  debugInfo: DebugInfo | null;
  isLoading: boolean;
  reasoningStatus: string | null;
  error: string | null;
  selectedProduct: RecommendationDTO | null;
  showDebug: boolean;
  phase: Phase;
  compareSet: string[];
  activeProductId: string | null;

  initSession: () => Promise<void>;
  send: (content: string) => Promise<void>;
  setSelectedProduct: (product: RecommendationDTO | null) => void;
  toggleDebug: () => void;
  reset: () => void;
  setPhase: (phase: Phase) => void;
  toggleCompare: (id: string) => void;
  setActiveProduct: (id: string) => void;
}

function getMockResponse(userMessage: string, questionNum: number): { content: string; reasoning: string } {
  const lower = userMessage.toLowerCase();

  if (questionNum === 1) {
    if (lower.includes('college') || lower.includes('campus') || lower.includes('university'))
      return { content: "Campus life — that means long days on your feet, commuting, classes, maybe some gym. Makes sense. How many hours are you typically on your feet each day?", reasoning: "Walking duration determines cushioning needs." };
    if (lower.includes('gym') || lower.includes('workout') || lower.includes('training') || lower.includes('lift'))
      return { content: "Gym use — got it. Stability for lifts, cushion for cardio. Are you on your feet for more than 3 hours most days, or mostly short sessions?", reasoning: "Duration + activity mix drives the recommendation." };
    if (lower.includes('run') || lower.includes('jog'))
      return { content: "Running — noted. Road or trails? And roughly how many hours a week are you out there?", reasoning: "Terrain and mileage shape cushioning and outsole choice." };
    if (lower.includes('walk'))
      return { content: "Walking focus — comfort and longevity will be key. How many hours a day are you typically on your feet?", reasoning: "Duration drives cushioning priority." };
    if (lower.includes('casual') || lower.includes('daily') || lower.includes('everyday') || lower.includes('work'))
      return { content: "Everyday wear — versatility matters here. How many hours are you typically on your feet each day?", reasoning: "Duration informs cushioning needs for all-day shoes." };
    return { content: "What will you mainly use these for — college, gym, running, walking, or casual everyday wear?", reasoning: "Use case is the foundation of the recommendation." };
  }

  if (questionNum === 2) {
    if (lower.includes('1') || lower.includes('2') || lower.includes('short') || lower.includes('few'))
      return { content: "Short sessions — so comfort over marathon endurance. Between comfort and style, which matters more to you right now?", reasoning: "Priority tradeoff shapes the shortlist." };
    if (lower.includes('6') || lower.includes('7') || lower.includes('8') || lower.includes('all day') || lower.includes('long'))
      return { content: "All-day wear — cushioning will be non-negotiable then. Between comfort and style, which do you lean toward?", reasoning: "With long hours, comfort should dominate." };
    return { content: "Good. Between comfort and style, which matters more to you right now?", reasoning: "Priority tradeoff shapes the shortlist." };
  }

  if (questionNum === 3) {
    if (lower.includes('comfort'))
      return { content: "Comfort first — I'll prioritize that. What's your budget range? (in ₹)", reasoning: "Budget determines the tier I search." };
    if (lower.includes('style') || lower.includes('look'))
      return { content: "Style-forward — noted. What's your budget range? (in ₹)", reasoning: "Budget determines the tier I search." };
    return { content: "Understood. What's your budget range? (in ₹)", reasoning: "Budget determines the tier I search." };
  }

  return { content: "Got it — one last thing. Any specific fit preferences? Wide toe box, snug fit, or no preference?", reasoning: "Fit prevents the #1 return reason." };
}

const MOCK_RECOMMENDATIONS: RecommendationDTO[] = [
  {
    id: '1', productId: 'prod-001', productName: 'Air Zoom Pegasus 41', productBrand: 'Nike',
    productImageUrl: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900&q=80',
    matchScore: 0.92, comfortScore: 88, durabilityScore: 85, styleScore: 82,
    reasoning: 'ReactX foam handles 6–8 hour days without breaking down, and the wider forefoot platform tolerates gym lateral movement better than v40. At ₹10,799 it lands inside your budget with ₹1,200 of headroom for a second pair of insoles.',
    tradeoffs: 'Athletic-leaning silhouette — reads less casual than lifestyle sneakers.',
    notSuitableFor: 'Formal or business-casual outfits.',
    tagline: 'All-rounder. Best for daily campus + light gym.',
    fitNote: 'True to size. Standard width.',
    weightG: 280, dropMm: 10,
    price: 10799, currency: 'INR', rank: 1,
    regretFlags: [{ type: 'WEIGHT', title: 'Heavier than minimalist trainers', description: '280g — noticeably bulkier than ultra-lights. If you have worn ultra-lights before, this will feel substantial.', severity: 'LOW' }],
    merchantOffers: [
      { merchantName: 'Nike India', price: 10799, currency: 'INR', deliveryEstimate: '2–3 days', returnPolicy: '30-day free returns', shippingCost: 0, inStock: true, checkoutUrl: '#', bestValue: true, whyRecommended: 'Official Nike India store — best price + free shipping.' },
      { merchantName: 'Myntra', price: 10799, currency: 'INR', deliveryEstimate: '4–6 days', returnPolicy: '45-day returns', shippingCost: 749, inStock: true, checkoutUrl: '#', bestValue: false, whyRecommended: 'Longest return window. Useful if sizing is uncertain.' },
    ],
  },
  {
    id: '2', productId: 'prod-003', productName: 'Fresh Foam X 1080v14', productBrand: 'New Balance',
    productImageUrl: 'https://images.unsplash.com/photo-1539185441755-769473a23570?w=900&q=80',
    matchScore: 0.88, comfortScore: 95, durabilityScore: 82, styleScore: 78,
    reasoning: 'Highest cushioning score of the shortlist — Fresh Foam X is the softest underfoot from this price tier. Hypoknit upper is the only one available in genuine wide width, which matters if you don\'t yet know your fit. ₹1,000 over your stated ceiling.',
    tradeoffs: 'Pillowy ride trades some gym stability — fine for treadmill, less ideal for squats.',
    notSuitableFor: 'Anyone who prefers a firm, responsive feel underfoot.',
    tagline: 'Maximum cushion. Best if comfort is non-negotiable.',
    fitNote: 'Runs true. Wide and X-wide available.',
    weightG: 295, dropMm: 6,
    price: 13299, currency: 'INR', rank: 2,
    regretFlags: [{ type: 'BUDGET', title: '₹1,300 over your stated budget', description: 'Your ceiling was ₹12,000. Showing this because it\'s the comfort leader.', severity: 'LOW' }],
    merchantOffers: [
      { merchantName: 'New Balance', price: 13299, currency: 'INR', deliveryEstimate: '3–4 days', returnPolicy: '30-day returns', shippingCost: 0, inStock: true, checkoutUrl: '#', bestValue: true, whyRecommended: 'Only seller with full wide-width inventory.' },
      { merchantName: 'Amazon India', price: 12899, currency: 'INR', deliveryEstimate: '1–2 days', returnPolicy: '30-day returns', shippingCost: 0, inStock: true, checkoutUrl: '#', bestValue: false, whyRecommended: 'Cheaper and faster — standard widths only.' },
    ],
  },
  {
    id: '3', productId: 'prod-004', productName: 'Ghost 16', productBrand: 'Brooks',
    productImageUrl: 'https://images.unsplash.com/photo-1551107696-a4b0c5a0d9a2?w=900&q=80',
    matchScore: 0.85, comfortScore: 85, durabilityScore: 88, styleScore: 75,
    reasoning: 'Highest durability score — DNA LOFT v3 outlasts the Pegasus midsole by ~150 miles in independent testing. 90-day return policy at Brooks is the longest of any merchant in your shortlist.',
    tradeoffs: 'Conservative silhouette. Won\'t turn heads on campus.',
    notSuitableFor: 'Anyone shopping primarily on aesthetics.',
    tagline: 'Most durable. Best if you\'ll wear them daily for a year+.',
    fitNote: 'Runs slightly narrow in the midfoot.',
    weightG: 286, dropMm: 12,
    price: 11630, currency: 'INR', rank: 3,
    regretFlags: [{ type: 'FIT', title: 'Runs narrow in midfoot', description: 'Brooks Ghost has been narrow for 6+ versions. If you have a wider foot, go up a half size.', severity: 'MEDIUM' }],
    merchantOffers: [
      { merchantName: 'Brooks India', price: 11630, currency: 'INR', deliveryEstimate: '3–5 days', returnPolicy: '90-day returns', shippingCost: 0, inStock: true, checkoutUrl: '#', bestValue: true, whyRecommended: '90-day return policy — best safety net for an unsure fit.' },
      { merchantName: 'Flipkart', price: 11499, currency: 'INR', deliveryEstimate: '3–5 days', returnPolicy: '10-day returns', shippingCost: 0, inStock: true, checkoutUrl: '#', bestValue: false, whyRecommended: 'Slightly cheaper with fast Flipkart delivery.' },
    ],
  },
];

export const useChatStore = create<ChatState>((set, get) => ({
  sessionId: null,
  messages: [],
  intent: null,
  recommendations: [],
  debugInfo: null,
  isLoading: false,
  reasoningStatus: null,
  error: null,
  selectedProduct: null,
  showDebug: false,
  phase: 'discovery',
  compareSet: [],
  activeProductId: null,

  initSession: async () => {
    try {
      const sessionId = await createSession();
      set({
        sessionId,
        messages: [{
          id: 'greeting', role: 'assistant',
          content: "Hey — I'm here to help you find shoes you won't regret. Tell me what you're shopping for, in your own words.",
          reasoningStatus: 'ready',
        }],
        error: null,
        phase: 'discovery',
      });
    } catch {
      set({
        sessionId: 'mock-session',
        messages: [{
          id: 'greeting', role: 'assistant',
          content: "Hey — I'm here to help you find shoes you won't regret. Tell me what you're shopping for, in your own words.",
          reasoningStatus: 'ready',
        }],
        error: null,
        phase: 'discovery',
      });
    }
  },

  send: async (content: string) => {
    const state = get();
    const userMessage: MessageDTO = { id: Date.now().toString(), role: 'user', content };
    set({ messages: [...state.messages, userMessage], isLoading: true, reasoningStatus: 'Analyzing your requirements…', error: null });

    try {
      if (state.sessionId && state.sessionId !== 'mock-session') {
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
      } else {
        // Mock mode
        await new Promise(r => setTimeout(r, 1200));
        const questionNum = state.messages.filter(m => m.role === 'user').length + 1;

        if (questionNum >= 4) {
          const assistantMessage: MessageDTO = {
            id: Date.now().toString(), role: 'assistant',
            content: "Clear picture. Here are 3 options I'd actually stand behind — and one I'm flagging a concern about. Tap any card to see my full reasoning.",
            reasoningStatus: 'Comfort-weighted shortlist, $130–$160 band, all three available with returns.',
          };
          set({
            messages: [...get().messages, assistantMessage],
            intent: {
              primaryUseCase: 'College + gym', walkingDuration: '6–8 hours / day',
              budget: 12000, comfortPriority: 0.85, stylePriority: 0.6, durabilityPriority: 0.7,
              terrainType: 'Urban', needsVersatility: true, confidenceScore: 0.82, missingAttributes: [],
            },
            recommendations: MOCK_RECOMMENDATIONS,
            isLoading: false, reasoningStatus: null,
            phase: 'shortlist',
            compareSet: MOCK_RECOMMENDATIONS.map(r => r.id),
            activeProductId: MOCK_RECOMMENDATIONS[0].id,
          });
        } else {
          const mock = getMockResponse(content, questionNum);
          const assistantMessage: MessageDTO = { id: Date.now().toString(), role: 'assistant', content: mock.content, reasoningStatus: mock.reasoning };
          const currentMissing = ['walkingDuration', 'budget', 'comfortPriority', 'stylePriority'].slice(questionNum - 1);
          set({
            messages: [...get().messages, assistantMessage],
            intent: {
              ...state.intent,
              confidenceScore: 0.2 + (questionNum * 0.15),
              missingAttributes: currentMissing,
              primaryUseCase: questionNum >= 1 ? content.substring(0, 40) : undefined,
            },
            isLoading: false, reasoningStatus: null,
          });
        }
      }
    } catch {
      set({ error: 'Failed to process message. Please try again.', isLoading: false, reasoningStatus: null });
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
    selectedProduct: null, showDebug: false, phase: 'discovery', compareSet: [], activeProductId: null,
  }),
}));
