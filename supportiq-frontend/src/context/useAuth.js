import { useContext } from 'react';
import { AuthContext } from './AuthContext';

/**
 * Custom hook to safely access authentication state and actions.
 * Throws a descriptive error if called outside of an AuthProvider.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default useAuth;
