import { createElement } from 'react'
import { Text, View } from 'react-native'
import { styles } from './formStyles'

export default function DatePickerField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {createElement('input', {
        type: 'date',
        value,
        onChange: (event: React.ChangeEvent<HTMLInputElement>) => onChange(event.currentTarget.value),
        style: {
          minHeight: 40,
          width: '100%',
          border: '1px solid #374151',
          padding: '0 10px',
          color: '#f3f4f6',
          backgroundColor: '#111416',
          fontSize: 12,
          colorScheme: 'dark',
        },
      })}
    </View>
  )
}
