# arr = []
# for x in range(-10, 10):
#     for y in range(-10, 10):
#         if x * x + y * y <= 9:
#             arr.append((x, y))
# arr.sort(key = lambda x: - x[0] * x[0] - x[1] * x[1])
# print(f"total {len(arr)}")
# print(", ".join(f"{pos[0]}" for pos in arr))
# print(", ".join(f"{pos[1]}" for pos in arr))

arr = []
for x in range(-10, 10):
    for y in range(-10, 10):
        sq = x * x + y * y
        if sq > 4 and sq <= 16:
            arr.append((x, y))
print(f"total {len(arr)}")
print(", ".join(f"{pos[0]}" for pos in arr))
print(", ".join(f"{pos[1]}" for pos in arr))
