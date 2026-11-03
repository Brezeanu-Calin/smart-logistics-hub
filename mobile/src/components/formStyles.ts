import { StyleSheet } from 'react-native'

export const styles = StyleSheet.create({
  field: { flex: 1, gap: 5, marginBottom: 3 },
  fieldLabel: { color: '#c1c7d0', fontSize: 10, fontWeight: '600' },
  dateButton: {
    minHeight: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: '#374151',
    paddingHorizontal: 10,
    backgroundColor: '#111416',
  },
  dateButtonText: { color: '#f3f4f6', fontSize: 12 },
  dateButtonIcon: { color: '#f59e0b', fontSize: 16 },
})
